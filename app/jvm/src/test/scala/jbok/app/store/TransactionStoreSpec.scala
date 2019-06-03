package jbok.app.store

import cats.effect.IO
import jbok.app.AppSpec
import jbok.app.service.store.TransactionStore
import jbok.core.StatefulGen
import jbok.core.ledger.BlockExecutor

class TransactionStoreSpec extends AppSpec {
  "insert and query transactions" in check { objects =>
    val executor = objects.get[BlockExecutor[IO]]
    val txStore  = objects.get[TransactionStore[IO]]

    val txs      = random(StatefulGen.transactions(min = 10, max = 10))
    val block    = random(StatefulGen.block(None, Some(txs)))
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
