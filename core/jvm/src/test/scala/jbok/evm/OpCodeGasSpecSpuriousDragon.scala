package jbok.evm

import cats.effect.IO
import jbok.common.testkit.random
import jbok.core.models.{Account, Address}
import jbok.core.testkit.uint256Gen
import jbok.evm.testkit._
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, Matchers}

class OpCodeGasSpecSpuriousDragon extends FunSuite with OpCodeTesting with Matchers with PropertyChecks {

  implicit override val config = EvmConfig.SpuriousDragonConfigBuilder(None)

  import config.feeSchedule._

  test(SELFDESTRUCT) { op =>
    // Sending refund to a non-existent account
    forAll { state: ProgramState[IO] =>
      val stack       = random[Stack](arbStack(op.delta, uint256Gen().filter(Address(_) != ownerAddr)).arbitrary)
      val stateIn     = state.withStack(stack)
      val (refund, _) = stateIn.stack.pop
      whenever(
        stateIn.world.getAccountOpt(Address(refund)).isEmpty.unsafeRunSync() && stateIn.ownBalance
          .unsafeRunSync() > 0) {
        val stateOut = op.execute(stateIn).unsafeRunSync()
        stateOut.gasRefund shouldEqual R_selfdestruct
        verifyGas(G_selfdestruct + G_newaccount, stateIn, stateOut)
      }
    }

    // Sending refund to an already existing account not dead account
    forAll { state: ProgramState[IO] =>
      val stack          = random[Stack](arbStack(op.delta, uint256Gen().filter(Address(_) != ownerAddr)).arbitrary)
      val stateIn        = state.withStack(stack)
      val (refund, _)    = stateIn.stack.pop
      val world          = stateIn.world.putAccount(Address(refund), Account.empty().increaseNonce())
      val updatedStateIn = stateIn.withWorld(world)
      val stateOut       = op.execute(updatedStateIn).unsafeRunSync()
      verifyGas(G_selfdestruct, updatedStateIn, stateOut)
      stateOut.gasRefund shouldEqual R_selfdestruct
    }

    // Owner account was already selfdestructed
    forAll { state: ProgramState[IO] =>
      val stack       = random[Stack](arbStack(op.delta, uint256Gen().filter(Address(_) != ownerAddr)).arbitrary)
      val stateIn     = state.withStack(stack)
      val (refund, _) = stateIn.stack.pop
      whenever(
        stateIn.world.getAccountOpt(Address(refund)).isEmpty.unsafeRunSync() && stateIn.ownBalance
          .unsafeRunSync() > 0) {
        val updatedStateIn = stateIn.withAddressToDelete(stateIn.context.env.ownerAddr)
        val stateOut       = op.execute(updatedStateIn).unsafeRunSync()
        verifyGas(G_selfdestruct + G_newaccount, updatedStateIn, stateOut)
        stateOut.gasRefund shouldEqual 0
      }
    }
  }
}
