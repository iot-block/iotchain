package jbok.app.store

import cats.effect.IO
import cats.implicits._
import jbok.app.AppSpec
import jbok.app.service.store.{BlockStore, TransactionStore}
import jbok.common.testkit._
import jbok.core.ledger.{BlockExecutor, History}
import jbok.core.models.{Block, SignedTransaction}
import jbok.core.testkit._

class ServiceStoreSpec extends AppSpec {
  "insert and query blocks" in check { objects =>
    val history    = objects.get[History[IO]]
    val executor   = objects.get[BlockExecutor[IO]]
    val blockStore = objects.get[BlockStore[IO]]

    val txs   = random[List[SignedTransaction]](genTxs(min = 10, max = 10))
    val block = random[Block](genBlock(stxsOpt = Some(txs)))

    for {
      executed <- executor.executeBlock(block)
      _        <- history.getBlockByNumber(0).map(_.get) >>= blockStore.insertBlock
      _        <- blockStore.insertBlock(executed.block)
      blocks   <- blockStore.findAllBlocks(1, 5)
      _ = blocks.length shouldBe 2
      genesis  <- blockStore.findBlockByNumber(0L)
      byHash   <- blockStore.findBlockByHash(executed.block.header.hash.toHex)
      byNumber <- blockStore.findBlockByNumber(1L)

      _ = genesis.isDefined shouldBe true
      _ = byHash.isDefined shouldBe true
      _ = byNumber.isDefined shouldBe true
      _ = byNumber shouldBe byHash
    } yield ()
  }

  "insert and query transactions" in check { objects =>
    val executor = objects.get[BlockExecutor[IO]]
    val txStore  = objects.get[TransactionStore[IO]]

    val txs      = random[List[SignedTransaction]](genTxs(min = 10, max = 10))
    val block    = random[Block](genBlock(stxsOpt = Some(txs)))
    val executed = executor.executeBlock(block).unsafeRunSync()

    for {
      _ <- txStore.insertBlockTransactions(executed.block, executed.receipts)
      result1 <- txStore
        .findTransactionsByAddress(txs.head.senderAddress.get.toString, 1, 5)
      _ = result1.length shouldBe 5
      result2 <- txStore
        .findTransactionsByAddress(txs.head.senderAddress.get.toString, 2, 5)

      _ = result1.length shouldBe 5
      _ = result2.length shouldBe 5
    } yield ()
  }
}
