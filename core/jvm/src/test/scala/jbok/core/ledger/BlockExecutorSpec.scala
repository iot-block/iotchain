package jbok.core.ledger

import cats.effect.IO
import jbok.JbokSpec
import jbok.common.testkit._
import jbok.core.testkit._
import jbok.common.execution._
import jbok.core.models._
import cats.implicits._
import jbok.core.ledger.TypedBlock.{ReceivedBlock, SyncBlocks}
import jbok.core.peer.Peer

class BlockExecutorSpec extends JbokSpec {
  implicit val fixture = defaultFixture()

  "BlockExecutor" should {

    "calculate the value, gas and reward transfer" in {
      val executor = random[BlockExecutor[IO]]
      val txs      = random[List[SignedTransaction]](genTxs(1, 1))
      val stx      = txs.head
      val sender   = SignedTransaction.getSender(stx).get
      val parent   = executor.history.getBestBlock.unsafeRunSync()
      val header   = executor.consensus.prepareHeader(Some(parent), Nil).unsafeRunSync()
      val world = executor.history
        .getWorldState(UInt256.Zero, Some(parent.header.stateRoot))
        .unsafeRunSync()
      val initSenderBalance =
        world.getBalance(sender).unsafeRunSync()
      val initMinerBalance =
        world.getBalance(Address(header.beneficiary)).unsafeRunSync()
      val initReceiverBalance =
        world.getBalance(stx.receivingAddress).unsafeRunSync()

      val result = executor.executeTransaction(stx, header, world, 0).unsafeRunSync()
      val gas    = UInt256(result.gasUsed * stx.gasPrice)
      result.world.getBalance(sender).unsafeRunSync() shouldBe (initSenderBalance - gas - stx.value)
      result.world.getBalance(Address(header.beneficiary)).unsafeRunSync() shouldBe (initMinerBalance + gas)
      result.world.getBalance(stx.receivingAddress).unsafeRunSync() shouldBe (initReceiverBalance + stx.value)
    }

    "change the nonce" in {
      val executor = random[BlockExecutor[IO]]
      val txs      = random[List[SignedTransaction]](genTxs(1, 1))
      val stx      = txs.head
      val sender   = SignedTransaction.getSender(stx).get
      val parent   = executor.history.getBestBlock.unsafeRunSync()
      val header   = executor.consensus.prepareHeader(Some(parent), Nil).unsafeRunSync()
      val world = executor.history
        .getWorldState(UInt256.Zero, Some(parent.header.stateRoot))
        .unsafeRunSync()
      val preNonce  = world.getAccount(sender).unsafeRunSync().nonce
      val result    = executor.executeTransaction(stx, header, world, 0).unsafeRunSync()
      val postNonce = result.world.getAccount(SignedTransaction.getSender(stx).get).unsafeRunSync().nonce

      postNonce shouldBe preNonce + 1
    }

    "executeBlock for a valid block without txs" in {
      val executor = random[BlockExecutor[IO]]
      val block    = random[List[Block]](genBlocks(1, 1)).head
      val result   = executor.handleSyncBlocks(SyncBlocks(block :: Nil, None)).unsafeRunSync()
      result shouldBe ()
    }

    "create sender account if it does not exists" in {
      val executor  = random[BlockExecutor[IO]]
      val txs       = random[List[SignedTransaction]](genTxs(10, 10))
      val block     = random[Block](genBlock(stxsOpt = Some(txs)))
      val peer      = random[Peer[IO]]
      val number    = executor.history.getBestBlockNumber.unsafeRunSync()
      val receivers = block.body.transactionList.map(_.receivingAddress)
      val xs =
        receivers.traverse[IO, Option[Account]](addr => executor.history.getAccount(addr, number)).unsafeRunSync()
      xs.forall(_.isEmpty) shouldBe true

      executor.handleReceivedBlock(ReceivedBlock(block, peer)).unsafeRunSync()

      val number2 = executor.history.getBestBlockNumber.unsafeRunSync()
      val xs2 =
        receivers.traverse[IO, Option[Account]](addr => executor.history.getAccount(addr, number2)).unsafeRunSync()
      xs2.forall(_.isDefined) shouldBe true
    }
  }
}
