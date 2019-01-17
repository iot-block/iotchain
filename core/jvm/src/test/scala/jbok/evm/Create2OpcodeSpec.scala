package jbok.evm

import jbok.JbokSpec
import jbok.common.execution._
import jbok.core.models.Address

class Create2OpcodeSpec extends JbokSpec {
  val config = EvmConfig.ConstantinopleConfigBuilder(None)
  import config.feeSchedule._
  val fxt = CreateOpFixture(config)

  "CREATE2" when {
    "initialization code executes normally" should {
      val result = CreateResult(fxt.contextForCreate2, fxt.endowment, fxt.createCode.code, CREATE2)

      "create a new contract" in {
        val newAccount = result.world.getAccount(fxt.newAddrByCreate2).unsafeRunSync()

        newAccount.balance shouldBe fxt.endowment
        result.world.getCode(fxt.newAddrByCreate2).unsafeRunSync() shouldBe fxt.contractCode.code
        result.world.getStorage(fxt.newAddrByCreate2).unsafeRunSync().load(0).unsafeRunSync() shouldBe 42
      }

      "update sender (creator) account" in {
        val initialCreator = result.context.world.getAccount(fxt.creatorAddr).unsafeRunSync()
        val updatedCreator = result.world.getAccount(fxt.creatorAddr).unsafeRunSync()

        updatedCreator.balance shouldBe initialCreator.balance - fxt.endowment
        updatedCreator.nonce shouldBe initialCreator.nonce + 1
      }

      "return the new contract's address" in {
        Address(result.returnValue) shouldBe fxt.newAddrByCreate2
      }

      "consume correct gas" in {
        result.stateOut.gasUsed shouldBe fxt.gasRequiredForCreate2
      }

      "step forward" in {
        result.stateOut.pc shouldBe result.stateIn.pc + 1
      }
    }

    "initialization code fails" should {
      val context = fxt.contextForCreate2.copy(startGas = G_create + fxt.gasRequiredForInit / 2)
      val result  = CreateResult(context, fxt.endowment, fxt.createCode.code, CREATE2)

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

      val context =
        fxt.contextForCreate2.copy(startGas = fxt.create2OpGasUsed + fxt.gasRequiredForInit + depositGas)
      val result = CreateResult(context, fxt.endowment, fxt.createCode.code, CREATE2)

      "consume all gas passed to the init code" in {
        val expectedGas = fxt.create2OpGasUsed + config.gasCap(context.startGas - fxt.create2OpGasUsed)
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
      val context = fxt.contextForCreate2.copy(env = env)
      val result  = CreateResult(context, fxt.endowment, fxt.createCode.code, CREATE2)

      "not modify world state" in {
        result.world shouldBe context.world
      }

      "return 0" in {
        result.returnValue shouldBe 0
      }

      "consume correct gas" in {
        result.stateOut.gasUsed shouldBe fxt.create2OpGasUsed
      }
    }

    "endowment value is greater than balance" should {
      val result = CreateResult(fxt.contextForCreate2, value = fxt.endowment * 2, fxt.createCode.code, CREATE2)

      "not modify world state" in {
        result.world shouldBe result.context.world
      }

      "return 0" in {
        result.returnValue shouldBe 0
      }

      "consume correct gas" in {
        result.stateOut.gasUsed shouldBe fxt.create2OpGasUsed
      }
    }
  }
}
