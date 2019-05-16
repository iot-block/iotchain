package jbok.evm

import cats.effect.IO
import jbok.common.testkit._
import jbok.core.CoreSpec
import jbok.core.ledger.History
import jbok.core.models.{Account, Address, UInt256}
import jbok.persistent.KeyValueDB
import scodec.bits._

case class CreateOpFixture(evmConfig: EvmConfig) extends CoreSpec {
  import evmConfig.feeSchedule._

  val creatorAddr        = Address(0xcafe)
  val endowment: UInt256 = 123
  val db            = KeyValueDB.inmem[IO].unsafeRunSync()
  val history       = History(db)
  val initWorld =
    history
      .getWorldState()
      .unsafeRunSync()
      .putAccount(creatorAddr, Account.empty().increaseBalance(endowment))

  // doubles the value passed in the input data
  val contractCode = Assembly(
    PUSH1,
    0,
    CALLDATALOAD,
    DUP1,
    ADD,
    PUSH1,
    0,
    MSTORE,
    PUSH1,
    32,
    PUSH1,
    0,
    RETURN
  )

  def initPart(contractCodeSize: Int): Assembly = Assembly(
    PUSH1,
    42,
    PUSH1,
    0,
    SSTORE, //store an arbitrary value
    PUSH1,
    contractCodeSize,
    DUP1,
    PUSH1,
    16,
    PUSH1,
    0,
    CODECOPY,
    PUSH1,
    0,
    RETURN
  )

  val initWithSelfDestruct = Assembly(
    PUSH1,
    creatorAddr.toUInt256.toInt,
    SELFDESTRUCT
  )

  val initWithSstoreWithClear = Assembly(
    //Save a value to the storage
    PUSH1,
    10,
    PUSH1,
    0,
    SSTORE,
    //Clear the store
    PUSH1,
    0,
    PUSH1,
    0,
    SSTORE
  )

  val createCode       = Assembly(initPart(contractCode.code.size.toInt).byteCode ++ contractCode.byteCode: _*)
  val salt             = hex"0x00000000000000000000000000000000000000000000000000000000cafebabe"
  val newAddrByCreate  = initWorld.createAddressWithOpCode(creatorAddr).unsafeRunSync()._1
  val newAddrByCreate2 = initWorld.create2AddressWithOpCode(creatorAddr, salt, createCode.code).unsafeRunSync()._1

  val copyCodeGas           = G_copy * wordsForBytes(contractCode.code.size) + evmConfig.calcMemCost(0, 0, contractCode.code.size)
  val codeHashGas           = G_sha3word * wordsForBytes(contractCode.code.size)
  val memGas                = evmConfig.calcMemCost(contractCode.code.size, 0, contractCode.code.size)
  val storeGas              = G_sset
  val createOpGasUsed       = G_create + memGas
  val create2OpGasUsed      = G_create + codeHashGas + memGas
  val gasRequiredForInit    = initPart(contractCode.code.size.toInt).linearConstGas(evmConfig) + copyCodeGas + storeGas
  val depositGas            = evmConfig.calcCodeDepositCost(contractCode.code)
  val gasRequiredForCreate  = gasRequiredForInit + depositGas + createOpGasUsed
  val gasRequiredForCreate2 = gasRequiredForInit + depositGas + create2OpGasUsed

  val env               = ExecEnv(creatorAddr, Address(0), Address(0), 1, ByteVector.empty, 0, Program(ByteVector.empty), null, 0)
  val contextForCreate  = ProgramContext(env, Address(0), 2 * gasRequiredForCreate, initWorld, evmConfig)
  val contextForCreate2 = ProgramContext(env, Address(0), 2 * gasRequiredForCreate2, initWorld, evmConfig)
}

case class CreateResult(
    context: ProgramContext[IO],
    value: UInt256,
    createCode: ByteVector,
    op: OpCode = CREATE
) {
  val mem  = Memory.empty.store(0, createCode)
  val salt = hex"0x00000000000000000000000000000000000000000000000000000000cafebabe"
  val stack =
    (if (op == CREATE2) Stack.empty().push(UInt256(salt)) else Stack.empty())
      .push(List[UInt256](createCode.size, 0, value))
  val stateIn  = ProgramState(context).withStack(stack).withMemory(mem)
  val stateOut = op.execute(stateIn).unsafeRunSync()

  val world       = stateOut.world
  val returnValue = stateOut.stack.pop._1
}
