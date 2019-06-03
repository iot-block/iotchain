package jbok.evm

import cats.effect.IO
import jbok.core.StatelessGen
import jbok.core.StatelessGen.uint256
import jbok.core.models.{Account, Address}

class OpCodeGasSpecSpuriousDragon extends OpCodeSpec {

  implicit override val evmConfig: EvmConfig = EvmConfig.SpuriousDragonConfigBuilder(None)

  import evmConfig.feeSchedule._

  test(SELFDESTRUCT) { op =>
    // Sending refund to a non-existent account
    forAll { state: ProgramState[IO] =>
      val stack       = random[Stack](StatelessGen.stack(op.delta, uint256().filter(Address(_) != ownerAddr)))
      val stateIn     = state.withStack(stack)
      val (refund, _) = stateIn.stack.pop
      whenever(
        stateIn.world.getAccountOpt(Address(refund)).isEmpty.unsafeRunSync() && stateIn.ownBalance
          .unsafeRunSync() > 0
      ) {
        val stateOut = op.execute(stateIn).unsafeRunSync()
        stateOut.gasRefund shouldBe R_selfdestruct
        verifyGas(G_selfdestruct + G_newaccount, stateIn, stateOut)
      }
    }

    // Sending refund to an already existing account not dead account
    forAll { state: ProgramState[IO] =>
      val stack          = random[Stack](StatelessGen.stack(op.delta, uint256().filter(Address(_) != ownerAddr)))
      val stateIn        = state.withStack(stack)
      val (refund, _)    = stateIn.stack.pop
      val world          = stateIn.world.putAccount(Address(refund), Account.empty().increaseNonce())
      val updatedStateIn = stateIn.withWorld(world)
      val stateOut       = op.execute(updatedStateIn).unsafeRunSync()
      verifyGas(G_selfdestruct, updatedStateIn, stateOut)
      stateOut.gasRefund shouldBe R_selfdestruct
    }

    // Owner account was already selfdestructed
    forAll { state: ProgramState[IO] =>
      val stack       = random[Stack](StatelessGen.stack(op.delta, uint256().filter(Address(_) != ownerAddr)))
      val stateIn     = state.withStack(stack)
      val (refund, _) = stateIn.stack.pop
      whenever(
        stateIn.world.getAccountOpt(Address(refund)).isEmpty.unsafeRunSync() && stateIn.ownBalance
          .unsafeRunSync() > 0
      ) {
        val updatedStateIn = stateIn.withAddressToDelete(stateIn.context.env.ownerAddr)
        val stateOut       = op.execute(updatedStateIn).unsafeRunSync()
        verifyGas(G_selfdestruct + G_newaccount, updatedStateIn, stateOut)
        stateOut.gasRefund shouldBe 0
      }
    }
  }
}
