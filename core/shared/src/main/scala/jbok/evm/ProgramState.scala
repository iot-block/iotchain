package jbok.evm

import cats.effect.Sync
import jbok.core.models.{Address, TxLogEntry, UInt256}
import scodec.bits.ByteVector

object ProgramState {
  def apply[F[_]: Sync](context: ProgramContext[F]): ProgramState[F] =
    ProgramState[F](
      context = context,
      gas = context.startGas,
      world = context.world,
      addressesToDelete = context.initialAddressesToDelete
    )
}

/**
  * Intermediate state updated with execution of each opcode in the program
  *
  * @param context the context which initiates the program
  * @param gas current gas for the execution
  * @param stack current stack
  * @param memory current memory
  * @param pc program counter - an index of the opcode in the program to be executed
  * @param returnData data to be returned from the program execution
  * @param gasRefund the amount of gas to be refunded after execution (not sure if a separate field is required)
  * @param addressesToDelete list of addresses of accounts scheduled to be deleted
  * @param internalTxs list of internal transactions (for debugging/tracing)
  * @param halted a flag to indicate program termination
  * @param error indicates whether the program terminated abnormally
  */
case class ProgramState[F[_]: Sync](
    context: ProgramContext[F],
    gas: BigInt,
    world: WorldStateProxy[F],
    stack: Stack = Stack.empty(),
    memory: Memory = Memory.empty,
    pc: Int = 0,
    returnData: ByteVector = ByteVector.empty,
    gasRefund: BigInt = 0,
    addressesToDelete: Set[Address] = Set.empty,
    internalTxs: List[InternalTransaction] = Nil,
    logs: List[TxLogEntry] = Nil,
    halted: Boolean = false,
    error: Option[ProgramError] = None
) {

  def config: EvmConfig = context.config

  def env: ExecEnv = context.env

  def ownAddress: Address = env.ownerAddr

  def ownBalance: F[UInt256] = world.getBalance(ownAddress)

  def storage: F[Storage[F]] = world.getStorage(ownAddress)

  def gasUsed: BigInt = context.startGas - gas

  def withWorld(updated: WorldStateProxy[F]): ProgramState[F] =
    copy(world = updated)

  def withStorage(updated: Storage[F]): ProgramState[F] =
    withWorld(world.saveStorage(ownAddress, updated))

  def program: Program = env.program

  def inputData: ByteVector = env.inputData

  def spendGas(amount: BigInt): ProgramState[F] =
    copy(gas = gas - amount)

  def refundGas(amount: BigInt): ProgramState[F] =
    copy(gasRefund = gasRefund + amount)

  def step(i: Int = 1): ProgramState[F] =
    copy(pc = pc + i)

  def goto(i: Int): ProgramState[F] =
    copy(pc = i)

  def withStack(stack: Stack): ProgramState[F] =
    copy(stack = stack)

  def withMemory(memory: Memory): ProgramState[F] =
    copy(memory = memory)

  def withError(error: ProgramError): ProgramState[F] =
    copy(error = Some(error), halted = true)

  def withReturnData(data: ByteVector): ProgramState[F] =
    copy(returnData = data)

  def withAddressToDelete(addr: Address): ProgramState[F] =
    copy(addressesToDelete = addressesToDelete + addr)

  def withAddressesToDelete(addresses: Set[Address]): ProgramState[F] =
    copy(addressesToDelete = addressesToDelete ++ addresses)

  def withLog(log: TxLogEntry): ProgramState[F] =
    copy(logs = logs :+ log)

  def withLogs(log: Seq[TxLogEntry]): ProgramState[F] =
    copy(logs = logs ++ log)

  def withInternalTxs(txs: Seq[InternalTransaction]): ProgramState[F] =
    if (config.traceInternalTransactions) copy(internalTxs = internalTxs ++ txs) else this

  def halt: ProgramState[F] =
    copy(halted = true)
}
