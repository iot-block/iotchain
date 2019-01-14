package jbok.evm

import jbok.JbokSpec
import jbok.common.execution._
import jbok.core.models.{Account, Address, UInt256}
import scodec.bits.ByteVector

class CreateOpcodeSpec extends JbokSpec {
  val config = EvmConfig.SpuriousDragonConfigBuilder(None)
  import config.feeSchedule._
  val fxt = CreateOpFixture(config)

  "CREATE" when {
    "initialization code executes normally" should {

      val result = CreateResult(fxt.contextForCreate, fxt.endowment, fxt.createCode.code)

      "create a new contract" in {
        val newAccount = result.world.getAccount(fxt.newAddrByCreate).unsafeRunSync()

        newAccount.balance shouldBe fxt.endowment
        result.world.getCode(fxt.newAddrByCreate).unsafeRunSync() shouldBe fxt.contractCode.code
        result.world.getStorage(fxt.newAddrByCreate).unsafeRunSync().load(0).unsafeRunSync() shouldBe 42
      }

      "update sender (creator) account" in {
        val initialCreator = result.context.world.getAccount(fxt.creatorAddr).unsafeRunSync()
        val updatedCreator = result.world.getAccount(fxt.creatorAddr).unsafeRunSync()

        updatedCreator.balance shouldBe initialCreator.balance - fxt.endowment
        updatedCreator.nonce shouldBe initialCreator.nonce + 1
      }

      "return the new contract's address" in {
        Address(result.returnValue) shouldBe fxt.newAddrByCreate
      }

      "consume correct gas" in {
        result.stateOut.gasUsed shouldBe fxt.gasRequiredForCreate
      }

      "step forward" in {
        result.stateOut.pc shouldBe result.stateIn.pc + 1
      }
    }

    "initialization code fails" should {
      val context = fxt.contextForCreate.copy(startGas = G_create + fxt.gasRequiredForInit / 2)
      val result  = CreateResult(context, fxt.endowment, fxt.createCode.code)

      "not modify world state except for the creator's nonce" in {
        val creatorsAccount = context.world.getAccount(fxt.creatorAddr).unsafeRunSync()
        val expectedWorld =
          context.world.putAccount(fxt.creatorAddr, creatorsAccount.copy(nonce = creatorsAccount.nonce + 1))
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
      val depositGas         = fxt.depositGas * 101 / 100
      val availableGasDepth0 = fxt.gasRequiredForInit + depositGas
      val availableGasDepth1 = config.gasCap(availableGasDepth0)
      val gasUsedInInit      = fxt.gasRequiredForInit + fxt.depositGas

      require(
        gasUsedInInit < availableGasDepth0 && gasUsedInInit > availableGasDepth1,
        "Regression: capped startGas in the VM at depth 1, should be used a base for code deposit gas check"
      )

      val context = fxt.contextForCreate.copy(startGas = G_create + fxt.gasRequiredForInit + depositGas)
      val result  = CreateResult(context, fxt.endowment, fxt.createCode.code)

      "consume all gas passed to the init code" in {
        val expectedGas = G_create + config.gasCap(context.startGas - G_create)
        result.stateOut.gasUsed shouldBe expectedGas
      }

      "not modify world state except for the creator's nonce" in {
        val creatorsAccount = context.world.getAccount(fxt.creatorAddr).unsafeRunSync()
        val expectedWorld =
          context.world.putAccount(fxt.creatorAddr, creatorsAccount.copy(nonce = creatorsAccount.nonce + 1))
        result.world shouldBe expectedWorld
      }

      "return 0" in {
        result.returnValue shouldBe 0
      }
    }

    "call depth limit is reached" should {
      val env     = fxt.env.copy(callDepth = EvmConfig.MaxCallDepth)
      val context = fxt.contextForCreate.copy(env = env)
      val result  = CreateResult(context, fxt.endowment, fxt.createCode.code)

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
      val result = CreateResult(fxt.contextForCreate, fxt.endowment * 2, fxt.createCode.code)

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
    val gasRequiredForInit     = fxt.initWithSelfDestruct.linearConstGas(config) + G_newaccount
    val gasRequiredForCreation = gasRequiredForInit + G_create

    val context = fxt.contextForCreate.copy(startGas = 2 * gasRequiredForCreation)
    val result  = CreateResult(context, fxt.endowment, fxt.initWithSelfDestruct.code)

    "refund the correct amount of gas" in {
      result.stateOut.gasRefund shouldBe result.stateOut.config.feeSchedule.R_selfdestruct
    }

  }

  "initialization includes a SSTORE opcode that clears the storage" should {

    val codeExecGas            = G_sreset + G_sset
    val gasRequiredForInit     = fxt.initWithSstoreWithClear.linearConstGas(config) + codeExecGas
    val gasRequiredForCreation = gasRequiredForInit + G_create

    val context = fxt.contextForCreate.copy(startGas = 2 * gasRequiredForCreation)
    val call    = CreateResult(context, fxt.endowment, fxt.initWithSstoreWithClear.code)

    "refund the correct amount of gas" in {
      call.stateOut.gasRefund shouldBe call.stateOut.config.feeSchedule.R_sclear
    }

  }

  "maxCodeSize check is enabled" should {
    val maxCodeSize = 30
    val ethConfig   = EvmConfig.ConstantinopleConfigBuilder(Some(maxCodeSize))

    val context = fxt.contextForCreate.copy(startGas = Int.MaxValue, config = ethConfig)

    val gasConsumedIfError = G_create + config.gasCap(context.startGas - G_create) //Gas consumed by CREATE opcode if an error happens

    "result in an out of gas if the code is larger than the limit" in {
      val codeSize          = maxCodeSize + 1
      val largeContractCode = Assembly((0 until codeSize).map(_ => Assembly.OpCodeAsByteCode(STOP)): _*)
      val createCode =
        Assembly(fxt.initPart(largeContractCode.code.size.toInt).byteCode ++ largeContractCode.byteCode: _*).code
      val call = CreateResult(context, fxt.endowment, createCode)

      call.stateOut.error shouldBe None
      call.stateOut.gasUsed shouldBe gasConsumedIfError
    }

    "not result in an out of gas if the code is smaller than the limit" in {
      val codeSize          = maxCodeSize - 1
      val largeContractCode = Assembly((0 until codeSize).map(_ => Assembly.OpCodeAsByteCode(STOP)): _*)
      val createCode =
        Assembly(fxt.initPart(largeContractCode.code.size.toInt).byteCode ++ largeContractCode.byteCode: _*).code
      val call = CreateResult(context, fxt.endowment, createCode)

      call.stateOut.error shouldBe None
      call.stateOut.gasUsed shouldNot be(gasConsumedIfError)
    }

  }

  "account with non-empty code already exists" should {

    "fail to create contract" in {
      val accountNonEmptyCode = Account(codeHash = ByteVector("abc".getBytes()))

      val world   = fxt.initWorld.putAccount(fxt.newAddrByCreate, accountNonEmptyCode)
      val context = fxt.contextForCreate.copy(world = world)
      val result  = CreateResult(context, fxt.endowment, fxt.createCode.code)

      result.returnValue shouldBe UInt256.Zero
      result.world.getAccount(fxt.newAddrByCreate).unsafeRunSync() shouldBe accountNonEmptyCode
      result.world.getCode(fxt.newAddrByCreate).unsafeRunSync() shouldBe ByteVector.empty
    }
  }

  "account with non-zero nonce already exists" should {
    "fail to create contract" in {
      val accountNonZeroNonce = Account(nonce = 1)

      val world   = fxt.initWorld.putAccount(fxt.newAddrByCreate, accountNonZeroNonce)
      val context = fxt.contextForCreate.copy(world = world)
      val result  = CreateResult(context, fxt.endowment, fxt.createCode.code)

      result.returnValue shouldBe UInt256.Zero
      result.world.getAccount(fxt.newAddrByCreate).unsafeRunSync() shouldBe accountNonZeroNonce
      result.world.getCode(fxt.newAddrByCreate).unsafeRunSync() shouldBe ByteVector.empty
    }
  }

  "account with non-zero balance, but empty code and zero nonce, already exists" should {
    "succeed in creating new contract" in {
      val accountNonZeroBalance = Account(balance = 1)
      val world                 = fxt.initWorld.putAccount(fxt.newAddrByCreate, accountNonZeroBalance)
      val context               = fxt.contextForCreate.copy(world = world)
      val result                = CreateResult(context, fxt.endowment, fxt.createCode.code)

      result.returnValue shouldBe fxt.newAddrByCreate.toUInt256

      val newContract = result.world.getAccount(fxt.newAddrByCreate).unsafeRunSync()
      newContract.balance shouldBe (accountNonZeroBalance.balance + fxt.endowment)
      newContract.nonce shouldBe accountNonZeroBalance.nonce + 1

      result.world.getCode(fxt.newAddrByCreate).unsafeRunSync() shouldBe fxt.contractCode.code
    }
  }
}
