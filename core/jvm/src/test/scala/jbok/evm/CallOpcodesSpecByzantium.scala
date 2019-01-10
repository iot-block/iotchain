package jbok.evm

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.ledger.History
import jbok.core.models.{Account, Address, UInt256}
import jbok.persistent.KeyValueDB
import scodec.bits.ByteVector
import jbok.common.testkit._
import jbok.common.execution._
import jbok.core.testkit._

class CallOpcodesSpecByzantium extends JbokSpec {

  val config     = EvmConfig.ByzantiumConfigBuilder(None)
  val history    = History.forBackendAndPath[IO](KeyValueDB.INMEM, "").unsafeRunSync()
  val startState = history.getWorldState(noEmptyAccounts = true).unsafeRunSync()

  val fxt = new CallOpFixture(config, startState)

  "STATICCALL" should {

    "call a program that executes a SELFDESTRUCT" should {
      val context = fxt.context.copy(world = fxt.worldWithSelfDestructProgram)
      val call    = fxt.CallResult(op = STATICCALL, context)

      "return 0" in {
        call.stateOut.stack.pop._1 shouldBe UInt256.Zero
      }
    }

    "call a precompiled contract" should {
      val contractAddress  = Address(1) // ECDSA recovery
      val invalidSignature = ByteVector(Array.fill(128)(0.toByte))
      val world            = fxt.worldWithoutExtAccount.putAccount(contractAddress, Account(balance = 1))
      val context          = fxt.context.copy(world = world)
      val call = fxt.CallResult(
        op = STATICCALL,
        context = context,
        to = contractAddress,
        inputData = invalidSignature,
        inOffset = 0,
        inSize = 128,
        outOffset = 0,
        outSize = 128
      )

      "compute a correct result" in {
        // For invalid signature the return data should be empty, so the memory should not be modified.
        // This is more interesting than checking valid signatures which are tested elsewhere
        val (result, _) = call.stateOut.memory.load(call.outOffset, call.outSize)
        val expected    = invalidSignature

        result shouldBe expected
      }

      "return 1" in {
        call.stateOut.stack.pop._1 shouldBe UInt256.One
      }

      "update precompiled contract's balance" in {
        call.extBalance shouldBe 1
      }
    }

    "call a program that executes a REVERT" should {
      val context = fxt.context.copy(world = fxt.worldWithRevertProgram)
      val call    = fxt.CallResult(op = STATICCALL, context)

      "return 0" in {
        call.stateOut.stack.pop._1 shouldBe UInt256.Zero
      }

      "consume correct gas" in {
        call.stateOut.gasUsed shouldBe 709
      }
    }
  }
}
