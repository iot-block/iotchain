package jbok.app.store

import cats.effect.IO
import jbok.app.AppSpec
import jbok.app.service.store.{BlockStore, TransactionStore}
import jbok.common.testkit._
import jbok.core.ledger.{BlockExecutor, History}
import jbok.core.models.{Block, SignedTransaction}
import jbok.core.testkit._

class ServiceStoreSpec extends AppSpec {
  val objects  = locator.unsafeRunSync()
  val history  = objects.get[History[IO]]
  val executor = objects.get[BlockExecutor[IO]]

  val txs      = random[List[SignedTransaction]](genTxs(min = 10, max = 10))
  val block    = random[Block](genBlock(stxsOpt = Some(txs)))
  val executed = executor.executeBlock(block).unsafeRunSync()

  val blockStore = objects.get[BlockStore[IO]]
  val txStore    = objects.get[TransactionStore[IO]]

  "insert and query blocks" in {
    blockStore.insertBlock(history.getBlockByNumber(0).unsafeRunSync().get).unsafeRunSync()
    blockStore.insertBlock(executed.block).unsafeRunSync()
    val blocks = blockStore.findAllBlocks(1, 5).unsafeRunSync()
    blocks.length shouldBe 2

    val genesis  = blockStore.findBlockByNumber(0L).unsafeRunSync()
    val byHash   = blockStore.findBlockByHash(executed.block.header.hash.toHex).unsafeRunSync()
    val byNumber = blockStore.findBlockByNumber(1L).unsafeRunSync()

    genesis.isDefined shouldBe true
    byHash.isDefined shouldBe true
    byNumber.isDefined shouldBe true
    byNumber shouldBe byHash
  }

  "insert and query transactions" in {
    txStore.insertBlockTransactions(executed.block, executed.receipts).unsafeRunSync()
    val result1 = txStore
      .findTransactionsByAddress(txs.head.senderAddress.get.toString, 1, 5)
      .unsafeRunSync()

    result1.length shouldBe 5

    val result2 = txStore
      .findTransactionsByAddress(txs.head.senderAddress.get.toString, 2, 5)
      .unsafeRunSync()

    result1.length shouldBe 5
    result2.length shouldBe 5
  }
}
