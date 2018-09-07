package jbok.evm

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.models.{Account, Address, UInt256}
import jbok.persistent.KeyValueDB
import scodec.bits.ByteVector

object CreateOpFixture {
  val config = EvmConfig.PostEIP161ConfigBuilder(None)
  import config.feeSchedule._

  val creatorAddr = Address(0xcafe)
  val endowment: UInt256 = 123
  val db = KeyValueDB.inMemory[IO].unsafeRunSync()
  val initWorld =
    WorldStateProxy
      .inMemory[IO](db)
      .unsafeRunSync()
      .putAccount(creatorAddr, Account.empty().increaseBalance(endowment))
  val newAddr = initWorld.createAddressWithOpCode(creatorAddr).unsafeRunSync()._1

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

  val createCode = Assembly(initPart(contractCode.code.size.toInt).byteCode ++ contractCode.byteCode: _*)
  val copyCodeGas = G_copy * wordsForBytes(contractCode.code.size) + config.calcMemCost(0, 0, contractCode.code.size)
  val storeGas = G_sset
  val gasRequiredForInit = initPart(contractCode.code.size.toInt).linearConstGas(config) + copyCodeGas + storeGas
  val depositGas = config.calcCodeDepositCost(contractCode.code)
  val gasRequiredForCreation = gasRequiredForInit + depositGas + G_create
  val env = ExecEnv(creatorAddr, Address(0), Address(0), 1, ByteVector.empty, 0, Program(ByteVector.empty), null, 0)
  val context = ProgramContext(env, Address(0), 2 * gasRequiredForCreation, initWorld, config)
}

class CreateOpcodeSpec extends JbokSpec {
  val config = EvmConfig.PostEIP161ConfigBuilder(None)
  import config.feeSchedule._

  case class CreateResult(
      context: ProgramContext[IO] = CreateOpFixture.context,
      value: UInt256 = CreateOpFixture.endowment,
      createCode: ByteVector = CreateOpFixture.createCode.code
  ) {
    val mem = Memory.empty.store(0, createCode)
    val stack = Stack.empty().push(Seq[UInt256](createCode.size, 0, value))
    val stateIn = ProgramState(context).withStack(stack).withMemory(mem)
    val stateOut = CREATE.execute(stateIn).unsafeRunSync()

    val world = stateOut.world
    val returnValue = stateOut.stack.pop._1
  }

  "CREATE" when {
    "initialization code executes normally" should {

      val result = CreateResult()

      "create a new contract" in {
        val newAccount = result.world.getAccount(CreateOpFixture.newAddr).unsafeRunSync()

        newAccount.balance shouldBe CreateOpFixture.endowment
        result.world.getCode(CreateOpFixture.newAddr).unsafeRunSync() shouldBe CreateOpFixture.contractCode.code
        result.world.getStorage(CreateOpFixture.newAddr).unsafeRunSync().load(0).unsafeRunSync() shouldBe 42
      }

      "update sender (creator) account" in {
        val initialCreator = result.context.world.getAccount(CreateOpFixture.creatorAddr).unsafeRunSync()
        val updatedCreator = result.world.getAccount(CreateOpFixture.creatorAddr).unsafeRunSync()

        updatedCreator.balance shouldBe initialCreator.balance - CreateOpFixture.endowment
        updatedCreator.nonce shouldBe initialCreator.nonce + 1
      }

      "return the new contract's address" in {
        Address(result.returnValue) shouldBe CreateOpFixture.newAddr
      }

      "consume correct gas" in {
        result.stateOut.gasUsed shouldBe CreateOpFixture.gasRequiredForCreation
      }

      "step forward" in {
        result.stateOut.pc shouldBe result.stateIn.pc + 1
      }
    }

    "initialization code fails" should {
      val context = CreateOpFixture.context.copy(startGas = G_create + CreateOpFixture.gasRequiredForInit / 2)
      val result = CreateResult(context = context)

      "not modify world state except for the creator's nonce" in {
        val creatorsAccount = context.world.getAccount(CreateOpFixture.creatorAddr).unsafeRunSync()
        val expectedWorld =
          context.world.putAccount(CreateOpFixture.creatorAddr,
                                    creatorsAccount.copy(nonce = creatorsAccount.nonce + 1))
        result.world shouldBe expectedWorld
      }

      "return 0" in {
        result.returnValue shouldBe 0
      }

      "consume correct gas" in {
        val expectedGas = G_create + config.gasCap(context.startGas - G_create)
        result.stateOut.gasUsed shouldBe expectedGas
      }

      "step forward" in {
        result.stateOut.pc shouldBe result.stateIn.pc + 1
      }
    }

    "initialization code runs normally but there's not enough gas to deposit code" should {
      val depositGas = CreateOpFixture.depositGas * 101 / 100
      val availableGasDepth0 = CreateOpFixture.gasRequiredForInit + depositGas
      val availableGasDepth1 = config.gasCap(availableGasDepth0)
      val gasUsedInInit = CreateOpFixture.gasRequiredForInit + CreateOpFixture.depositGas

      require(
        gasUsedInInit < availableGasDepth0 && gasUsedInInit > availableGasDepth1,
        "Regression: capped startGas in the VM at depth 1, should be used a base for code deposit gas check"
      )

      val context = CreateOpFixture.context.copy(startGas = G_create + CreateOpFixture.gasRequiredForInit + depositGas)
      val result = CreateResult(context = context)

      "consume all gas passed to the init code" in {
        val expectedGas = G_create + config.gasCap(context.startGas - G_create)
        result.stateOut.gasUsed shouldBe expectedGas
      }

      "not modify world state except for the creator's nonce" in {
        val creatorsAccount = context.world.getAccount(CreateOpFixture.creatorAddr).unsafeRunSync()
        val expectedWorld =
          context.world.putAccount(CreateOpFixture.creatorAddr,
                                    creatorsAccount.copy(nonce = creatorsAccount.nonce + 1))
        result.world shouldBe expectedWorld
      }

      "return 0" in {
        result.returnValue shouldBe 0
      }
    }

    "call depth limit is reached" should {
      val env = CreateOpFixture.env.copy(callDepth = EvmConfig.MaxCallDepth)
      val context = CreateOpFixture.context.copy(env = env)
      val result = CreateResult(context = context)

      "not modify world state" in {
        result.world shouldBe context.world
      }

      "return 0" in {
        result.returnValue shouldBe 0
      }

      "consume correct gas" in {
        result.stateOut.gasUsed shouldBe G_create
      }
    }

    "endowment value is greater than balance" should {
      val result = CreateResult(value = CreateOpFixture.endowment * 2)

      "not modify world state" in {
        result.world shouldBe result.context.world
      }

      "return 0" in {
        result.returnValue shouldBe 0
      }

      "consume correct gas" in {
        result.stateOut.gasUsed shouldBe G_create
      }
    }
  }

  "initialization includes SELFDESTRUCT opcode" should {
    val gasRequiredForInit = CreateOpFixture.initWithSelfDestruct.linearConstGas(config) + G_newaccount
    val gasRequiredForCreation = gasRequiredForInit + G_create

    val context = CreateOpFixture.context.copy(startGas = 2 * gasRequiredForCreation)
    val result = CreateResult(context = context, createCode = CreateOpFixture.initWithSelfDestruct.code)

    "refund the correct amount of gas" in {
      result.stateOut.gasRefund shouldBe result.stateOut.config.feeSchedule.R_selfdestruct
    }

  }

  "initialization includes a SSTORE opcode that clears the storage" should {

    val codeExecGas = G_sreset + G_sset
    val gasRequiredForInit = CreateOpFixture.initWithSstoreWithClear.linearConstGas(config) + codeExecGas
    val gasRequiredForCreation = gasRequiredForInit + G_create

    val context = CreateOpFixture.context.copy(startGas = 2 * gasRequiredForCreation)
    val call = CreateResult(context = context, createCode = CreateOpFixture.initWithSstoreWithClear.code)

    "refund the correct amount of gas" in {
      call.stateOut.gasRefund shouldBe call.stateOut.config.feeSchedule.R_sclear
    }

  }

  "maxCodeSize check is enabled" should {
    val maxCodeSize = 30
    val ethConfig = EvmConfig.PostEIP160ConfigBuilder(Some(maxCodeSize))

    val context = CreateOpFixture.context.copy(startGas = Int.MaxValue, config = ethConfig)

    val gasConsumedIfError = G_create + config.gasCap(context.startGas - G_create) //Gas consumed by CREATE opcode if an error happens

    "result in an out of gas if the code is larger than the limit" in {
      val codeSize = maxCodeSize + 1
      val largeContractCode = Assembly((0 until codeSize).map(_ => Assembly.OpCodeAsByteCode(STOP)): _*)
      val createCode =
        Assembly(CreateOpFixture.initPart(largeContractCode.code.size.toInt).byteCode ++ largeContractCode.byteCode: _*).code
      val call = CreateResult(context = context, createCode = createCode)

      call.stateOut.error shouldBe None
      call.stateOut.gasUsed shouldBe gasConsumedIfError
    }

    "not result in an out of gas if the code is smaller than the limit" in {
      val codeSize = maxCodeSize - 1
      val largeContractCode = Assembly((0 until codeSize).map(_ => Assembly.OpCodeAsByteCode(STOP)): _*)
      val createCode =
        Assembly(CreateOpFixture.initPart(largeContractCode.code.size.toInt).byteCode ++ largeContractCode.byteCode: _*).code
      val call = CreateResult(context = context, createCode = createCode)

      call.stateOut.error shouldBe None
      call.stateOut.gasUsed shouldNot be(gasConsumedIfError)
    }

  }

  "account with non-empty code already exists" should {

    "fail to create contract" in {
      val accountNonEmptyCode = Account(codeHash = ByteVector("abc".getBytes()))

      val world = CreateOpFixture.initWorld.putAccount(CreateOpFixture.newAddr, accountNonEmptyCode)
      val context = CreateOpFixture.context.copy(world = world)
      val result = CreateResult(context = context)

      result.returnValue shouldBe UInt256.Zero
      result.world.getAccount(CreateOpFixture.newAddr).unsafeRunSync() shouldBe accountNonEmptyCode
      result.world.getCode(CreateOpFixture.newAddr).unsafeRunSync() shouldBe ByteVector.empty
    }
  }

  "account with non-zero nonce already exists" should {

    "fail to create contract" in {
      val accountNonZeroNonce = Account(nonce = 1)

      val world = CreateOpFixture.initWorld.putAccount(CreateOpFixture.newAddr, accountNonZeroNonce)
      val context = CreateOpFixture.context.copy(world = world)
      val result = CreateResult(context = context)

      result.returnValue shouldBe UInt256.Zero
      result.world.getAccount(CreateOpFixture.newAddr).unsafeRunSync() shouldBe accountNonZeroNonce
      result.world.getCode(CreateOpFixture.newAddr).unsafeRunSync() shouldBe ByteVector.empty
    }
  }

  "account with non-zero balance, but empty code and zero nonce, already exists" should {

    "succeed in creating new contract" in {
      val accountNonZeroBalance = Account(balance = 1)

      val world = CreateOpFixture.initWorld.putAccount(CreateOpFixture.newAddr, accountNonZeroBalance)
      val context = CreateOpFixture.context.copy(world = world)
      val result = CreateResult(context = context)

      result.returnValue shouldBe CreateOpFixture.newAddr.toUInt256

      val newContract = result.world.getAccount(CreateOpFixture.newAddr).unsafeRunSync()
      newContract.balance shouldBe (accountNonZeroBalance.balance + CreateOpFixture.endowment)
      newContract.nonce shouldBe accountNonZeroBalance.nonce

      result.world.getCode(CreateOpFixture.newAddr).unsafeRunSync() shouldBe CreateOpFixture.contractCode.code
    }
  }
}
