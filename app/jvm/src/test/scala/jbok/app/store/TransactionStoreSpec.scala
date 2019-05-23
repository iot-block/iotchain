package jbok.app.store

import cats.effect.IO
import jbok.app.AppSpec
import jbok.app.service.store.TransactionStore
import jbok.common.testkit._
import jbok.core.ledger.BlockExecutor
import jbok.core.models.{Block, SignedTransaction}
import jbok.core.testkit._

class TransactionStoreSpec extends AppSpec {
  val keyPair = Some(testMiner.keyPair)
  "insert and query transactions" in check { objects =>
    val executor = objects.get[BlockExecutor[IO]]
    val txStore  = objects.get[TransactionStore[IO]]

    val txs      = random[List[SignedTransaction]](genTxs(min = 10, max = 10))
    val block    = random[Block](genBlock(stxsOpt = Some(txs)))
    val executed = executor.executeBlock(block).unsafeRunSync()

    for {
      _       <- txStore.insertBlockTransactions(executed.block, executed.receipts)
      result1 <- txStore.findTransactionsByAddress(txs.head.senderAddress.get.toString, 1, 5)
      _ = result1.length shouldBe 5
      result2 <- txStore.findTransactionsByAddress(txs.head.senderAddress.get.toString, 2, 5)
      _ = result1.length shouldBe 5
      _ = result2.length shouldBe 5
    } yield ()
  }
}
