package jbok.app.service

import cats.effect.IO
import jbok.app.AppSpec
import jbok.core.ledger.History
import jbok.core.mining.BlockMiner
import jbok.core.models.{Address, SignedTransaction, Transaction}
import jbok.common.testkit._
import jbok.core.testkit._
import jbok.core.api.{BlockTag, TransactionAPI}
import scodec.bits.ByteVector

class TransactionAPISpec extends AppSpec {
  "TransactionAPI" should {
    "return tx by hash & number" in {
      val objects     = locator.unsafeRunSync()
      val history     = objects.get[History[IO]]
      val miner       = objects.get[BlockMiner[IO]]
      val transaction = objects.get[TransactionAPI[IO]]

      val txs    = random[List[SignedTransaction]](genTxs(10, 10))
      val parent = history.getBestBlock.unsafeRunSync()
      val stx    = txs.head

      transaction.sendTx(stx).unsafeRunSync()
      transaction.getTx(stx.hash).unsafeRunSync() shouldBe None
      transaction.getPendingTx(stx.hash).unsafeRunSync() shouldBe Some(stx)

      val List(minedBlock) = miner.stream.take(1).compile.toList.unsafeRunSync()
      transaction.getTx(stx.hash).unsafeRunSync() shouldBe Some(stx)
      transaction.getPendingTx(stx.hash).unsafeRunSync() shouldBe None
      transaction.getReceipt(stx.hash).unsafeRunSync().nonEmpty shouldBe true
      transaction
        .getTxByBlockHashAndIndex(minedBlock.block.header.hash, 0)
        .unsafeRunSync()
        .get shouldBe stx
      transaction
        .getTxByBlockTagAndIndex(BlockTag(1), 0)
        .unsafeRunSync()
        .get shouldBe stx
      transaction
        .getTxByBlockTagAndIndex(BlockTag.latest, 0)
        .unsafeRunSync()
        .get shouldBe stx
      transaction
        .getTxByBlockTagAndIndex(BlockTag(2), 0)
        .unsafeRunSync() shouldBe None
    }

    "return tx by hash & number send a bad stx" in {
      val objects     = locator.unsafeRunSync()
      val history     = objects.get[History[IO]]
      val miner       = objects.get[BlockMiner[IO]]
      val transaction = objects.get[TransactionAPI[IO]]

      val tx = Transaction(1, 1, 21000, Address.empty, 100000, ByteVector.empty)
//      val parent = history.getBestBlock.unsafeRunSync()
      val stx = SignedTransaction.sign[IO](tx, testMiner.keyPair).unsafeRunSync()

      transaction.sendTx(stx).unsafeRunSync()

      transaction.getTx(stx.hash).unsafeRunSync() shouldBe None
      transaction.getPendingTx(stx.hash).unsafeRunSync() shouldBe Some(stx)

      miner.stream.take(1).compile.toList.unsafeRunSync()

      transaction.getTx(stx.hash).unsafeRunSync() shouldBe None
      transaction.getPendingTx(stx.hash).unsafeRunSync() shouldBe None
      transaction.getReceipt(stx.hash).unsafeRunSync().isEmpty shouldBe true
    }

    "sendTx" in {
      val objects     = locator.unsafeRunSync()
      val history     = objects.get[History[IO]]
      val miner       = objects.get[BlockMiner[IO]]
      val transaction = objects.get[TransactionAPI[IO]]

      val stx = random[List[SignedTransaction]](genTxs(1, 1)).head
      transaction.sendTx(stx).unsafeRunSync() shouldBe stx.hash

      miner.stream.take(1).compile.toList.unsafeRunSync()
      history.getBestBlock.unsafeRunSync().body.transactionList.head shouldBe stx
    }

    "sendRawTx" in {
      val objects     = locator.unsafeRunSync()
      val history     = objects.get[History[IO]]
      val miner       = objects.get[BlockMiner[IO]]
      val transaction = objects.get[TransactionAPI[IO]]

      val stx = random[List[SignedTransaction]](genTxs(1, 1)).head
      transaction.sendRawTx(stx.bytes).unsafeRunSync() shouldBe stx.hash

      miner.stream.take(1).compile.toList.unsafeRunSync()
      history.getBestBlock.unsafeRunSync().body.transactionList.head shouldBe stx
    }
  }
}
