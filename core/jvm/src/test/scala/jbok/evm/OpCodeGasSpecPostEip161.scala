package jbok.evm

import jbok.core.models.{Account, Address}
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, Matchers}
import jbok.testkit.VMGens._

class OpCodeGasSpecPostEip161 extends FunSuite with OpCodeTesting with Matchers with PropertyChecks {

  override val config = EvmConfig.PostEIP161ConfigBuilder(None)

  import config.feeSchedule._

  test(SELFDESTRUCT) { op =>
    val stateGen = getProgramStateGen(
      stackGen = getStackGen(elems = 1),
      evmConfig = config
    )

    // Sending refund to a non-existent account
    forAll(stateGen) { stateIn =>
      val (refund, _) = stateIn.stack.pop
      whenever(
        stateIn.world.getAccountOpt(Address(refund)).isEmpty.unsafeRunSync() && stateIn.ownBalance.unsafeRunSync() > 0) {
        val stateOut = op.execute(stateIn).unsafeRunSync()
        stateOut.gasRefund shouldEqual R_selfdestruct
        verifyGas(G_selfdestruct + G_newaccount, stateIn, stateOut)
      }
    }

    // Sending refund to an already existing account not dead account
    forAll(stateGen) { stateIn =>
      val (refund, _) = stateIn.stack.pop
      val world = stateIn.world.putAccount(Address(refund), Account.empty().increaseNonce())
      val updatedStateIn = stateIn.withWorld(world)
      val stateOut = op.execute(updatedStateIn).unsafeRunSync()
      verifyGas(G_selfdestruct, updatedStateIn, stateOut)
      stateOut.gasRefund shouldEqual R_selfdestruct
    }

    // Owner account was already selfdestructed
    forAll(stateGen) { stateIn =>
      val (refund, _) = stateIn.stack.pop
      whenever(
        stateIn.world.getAccountOpt(Address(refund)).isEmpty.unsafeRunSync() && stateIn.ownBalance.unsafeRunSync() > 0) {
        val updatedStateIn = stateIn.withAddressToDelete(stateIn.context.env.ownerAddr)
        val stateOut = op.execute(updatedStateIn).unsafeRunSync()
        verifyGas(G_selfdestruct + G_newaccount, updatedStateIn, stateOut)
        stateOut.gasRefund shouldEqual 0
      }
    }
  }
}
