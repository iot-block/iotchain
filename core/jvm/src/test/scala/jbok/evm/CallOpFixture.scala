package jbok.evm

import cats.effect.IO
import jbok.core.models.{Account, Address, UInt256}
import jbok.crypto._
import jbok.evm._
import jbok.testkit.VMGens
import scodec.bits.ByteVector

class CallOpFixture(val config: EvmConfig, val startState: WorldStateProxy[IO]) {
  import config.feeSchedule._

  val ownerAddr = Address(0xcafebabe)
  val extAddr = Address(0xfacefeed)
  val callerAddr = Address(0xdeadbeef)

  val ownerOffset = UInt256(0)
  val callerOffset = UInt256(1)
  val valueOffset = UInt256(2)

  val extCode = Assembly(
    //store owner address
    ADDRESS,
    PUSH1,
    ownerOffset.toInt,
    SSTORE,
    //store caller address
    CALLER,
    PUSH1,
    callerOffset.toInt,
    SSTORE,
    //store call value
    CALLVALUE,
    PUSH1,
    valueOffset.toInt,
    SSTORE,
    // return first half of unmodified input data
    PUSH1,
    2,
    CALLDATASIZE,
    DIV,
    PUSH1,
    0,
    DUP2,
    DUP2,
    DUP1,
    CALLDATACOPY,
    RETURN
  )

  val selfDestructCode = Assembly(
    PUSH20,
    callerAddr.bytes,
    SELFDESTRUCT
  )

  val selfDestructTransferringToSelfCode = Assembly(
    PUSH20,
    extAddr.bytes,
    SELFDESTRUCT
  )

  val sstoreWithClearCode = Assembly(
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

  val valueToReturn = 23
  val returnSingleByteProgram = Assembly(
    PUSH1,
    valueToReturn,
    PUSH1,
    0,
    MSTORE,
    PUSH1,
    1,
    PUSH1,
    31,
    RETURN
  )

  val inputData = VMGens.getUInt256Gen().sample.get.bytes
  val expectedMemCost = config.calcMemCost(inputData.size, inputData.size, inputData.size / 2)

  val initialBalance = UInt256(1000)

  val requiredGas = {
    val storageCost = 3 * G_sset
    val memCost = config.calcMemCost(0, 0, 32)
    val copyCost = G_copy * wordsForBytes(32)

    extCode.linearConstGas(config) + storageCost + memCost + copyCost
  }

  val gasMargin = 13

  val initialOwnerAccount = Account(balance = initialBalance)

  val extProgram = extCode.program
  val invalidProgram = Program(extProgram.code.init :+ INVALID.code.toByte)
  val selfDestructProgram = selfDestructCode.program
  val sstoreWithClearProgram = sstoreWithClearCode.program
  val accountWithCode: ByteVector => Account = code => Account.empty().withCode(code.kec256)

  val worldWithoutExtAccount = startState.putAccount(ownerAddr, initialOwnerAccount)

  val worldWithExtAccount = worldWithoutExtAccount
    .putAccount(extAddr, accountWithCode(extProgram.code))
    .putCode(extAddr, extProgram.code)

  val worldWithExtEmptyAccount = worldWithoutExtAccount.putAccount(extAddr, Account.empty())

  val worldWithInvalidProgram = worldWithoutExtAccount
    .putAccount(extAddr, accountWithCode(invalidProgram.code))
    .putCode(extAddr, invalidProgram.code)

  val worldWithSelfDestructProgram = worldWithoutExtAccount
    .putAccount(extAddr, accountWithCode(selfDestructProgram.code))
    .putCode(extAddr, selfDestructCode.code)

  val worldWithSelfDestructSelfProgram = worldWithoutExtAccount
    .putAccount(extAddr, Account.empty())
    .putCode(extAddr, selfDestructTransferringToSelfCode.code)

  val worldWithSstoreWithClearProgram = worldWithoutExtAccount
    .putAccount(extAddr, accountWithCode(sstoreWithClearProgram.code))
    .putCode(extAddr, sstoreWithClearCode.code)

  val worldWithReturnSingleByteCode = worldWithoutExtAccount
    .putAccount(extAddr, accountWithCode(returnSingleByteProgram.code))
    .putCode(extAddr, returnSingleByteProgram.code)

  val env = ExecEnv(ownerAddr, callerAddr, callerAddr, 1, ByteVector.empty, 123, Program(ByteVector.empty), null, 0)
  val context = ProgramContext(env, ownerAddr, 2 * requiredGas, worldWithExtAccount, config)

  case class CallResult(
      op: CallOp,
      context: ProgramContext[IO] = context,
      inputData: ByteVector = inputData,
      gas: BigInt = requiredGas + gasMargin,
      to: Address = extAddr,
      value: UInt256 = initialBalance / 2,
      inOffset: UInt256 = UInt256.Zero,
      inSize: UInt256 = inputData.size,
      outOffset: UInt256 = inputData.size,
      outSize: UInt256 = inputData.size / 2
  ) {
    private val params = Seq(UInt256(gas), to.toUInt256, value, inOffset, inSize, outOffset, outSize).reverse
    private val paramsForDelegate = params.take(4) ++ params.drop(5)
    private val stack = Stack.empty().push(if (op == DELEGATECALL) paramsForDelegate else params)
    private val mem = Memory.empty.store(UInt256.Zero, inputData)

    val stateIn = ProgramState[IO](context).withStack(stack).withMemory(mem)
    val stateOut = op.execute(stateIn).unsafeRunSync()
    val world = stateOut.world

    val ownBalance: UInt256 = world.getBalance(context.env.ownerAddr).unsafeRunSync()
    val extBalance: UInt256 = world.getBalance(to).unsafeRunSync()

    val ownStorage = world.getStorage(context.env.ownerAddr).unsafeRunSync()
    val extStorage = world.getStorage(to).unsafeRunSync()
  }
}

