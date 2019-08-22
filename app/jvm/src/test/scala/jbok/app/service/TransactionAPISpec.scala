package jbok.app.service

import cats.effect.IO
import jbok.app.AppSpec
import jbok.core.StatefulGen
import jbok.core.api.{BlockTag, TransactionAPI}
import jbok.core.ledger.History
import jbok.core.mining.BlockMiner
import jbok.core.models.{Address, SignedTransaction, Transaction}
import scodec.bits.ByteVector

class TransactionAPISpec extends AppSpec {
  "TransactionAPI" should {
    "return tx by hash & number" in check { objects =>
      val history     = objects.get[History[IO]]
      val miner       = objects.get[BlockMiner[IO]]
      val transaction = objects.get[TransactionAPI[IO]]
      val txs         = random(StatefulGen.transactions(10, 10))
      val stx         = txs.head

      for {
        parent <- history.getBestBlock
        _      <- transaction.sendTx(stx)
        res    <- transaction.getTx(stx.hash)
        _ = res shouldBe None
        res <- transaction.getPendingTx(stx.hash)
        _ = res shouldBe Some(stx)
        minedBlock <- miner.mine()
        res        <- transaction.getTx(stx.hash)
        _ = res shouldBe Some(stx)
        res <- transaction.getPendingTx(stx.hash)
        _ = res shouldBe None
        res <- transaction.getReceipt(stx.hash).map(_.nonEmpty)
        _ = res shouldBe true
        res <- transaction.getTxByBlockHashAndIndex(minedBlock.block.header.hash, 0)
        _ = res shouldBe Some(stx)
        res <- transaction getTxByBlockTagAndIndex (BlockTag(1), 0)
        _ = res shouldBe Some(stx)
        res <- transaction.getTxByBlockTagAndIndex(BlockTag.latest, 0)
        _ = res shouldBe Some(stx)
        res <- transaction.getTxByBlockTagAndIndex(BlockTag(2), 0)
        _ = res shouldBe None
      } yield ()
    }

    "return tx by hash & number send a bad stx" in check { objects =>
      val miner       = objects.get[BlockMiner[IO]]
      val transaction = objects.get[TransactionAPI[IO]]

      val tx = Transaction(1, 1, 21000, Some(Address.empty), 100000, ByteVector.empty)

      for {
        stx <- SignedTransaction.sign[IO](tx, testKeyPair, chainId)
        _   <- transaction.sendTx(stx)
        res <- transaction.getTx(stx.hash)
        _ = res shouldBe None
        res <- transaction.getPendingTx(stx.hash)
        _ = res shouldBe Some(stx)
        _   <- miner.mine()
        res <- transaction.getTx(stx.hash)
        _ = res shouldBe None
        res <- transaction.getPendingTx(stx.hash).map(_.isEmpty)
        _ = res shouldBe false
        res <- transaction.getReceipt(stx.hash).map(_.isEmpty)
        _ = res shouldBe true
      } yield ()
    }

    "sendTx" in check { objects =>
      val history     = objects.get[History[IO]]
      val miner       = objects.get[BlockMiner[IO]]
      val transaction = objects.get[TransactionAPI[IO]]

      val stx = random(StatefulGen.transactions(1, 1)).head

      for {
        res <- transaction.sendTx(stx)
        _ = res shouldBe stx.hash
        _   <- miner.mine()
        res <- history.getBestBlock.map(_.body.transactionList.head)
        _ = res shouldBe stx
      } yield ()
    }

    "sendRawTx" in check { objects =>
      val history     = objects.get[History[IO]]
      val miner       = objects.get[BlockMiner[IO]]
      val transaction = objects.get[TransactionAPI[IO]]

      val stx = random(StatefulGen.transactions(1, 1)).head

      for {
        res <- transaction.sendRawTx(stx.bytes)
        _ = res shouldBe stx.hash
        _   <- miner.mine()
        res <- history.getBestBlock.map(_.body.transactionList.head)
        _ = res shouldBe stx
      } yield ()
    }
  }
}
