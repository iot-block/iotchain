package jbok.app.service

import better.files._
import cats.effect.IO
import jbok.JbokSpec
import jbok.app.service.store.{Migration, ServiceStore}
import jbok.common.execution._
import jbok.common.testkit._
import jbok.core.ledger.BlockExecutor
import jbok.core.models.{Block, SignedTransaction}
import jbok.core.testkit._
import monix.eval.Task
import monix.eval.instances.CatsConcurrentEffectForTask
import monix.execution.Scheduler

class ServiceStoreSpec extends JbokSpec {
  implicit val config = testConfig
  val executor        = random[BlockExecutor[IO]]
  val txs             = random[List[SignedTransaction]](genTxs(min = 10, max = 10))
  val block           = random[Block](genBlock(stxsOpt = Some(txs)))
  val executed        = executor.executeBlock(block).unsafeRunSync()

  def check(backend: String, store: ServiceStore[IO]): Unit = {
    val blockStore       = store.blockStore
    val transactionStore = store.transactionStore

    "insert and query blocks" in {
      blockStore.insertBlock(executor.history.getBlockByNumber(0).unsafeRunSync().get).unsafeRunSync()
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
      transactionStore.insertBlockTransactions(executed.block, executed.receipts).unsafeRunSync()
      val result1 = transactionStore
        .findTransactionsByAddress(txs.head.senderAddress.get.toString, 1, 5)
        .unsafeRunSync()

      result1.length shouldBe 5

      val result2 = transactionStore
        .findTransactionsByAddress(txs.head.senderAddress.get.toString, 2, 5)
        .unsafeRunSync()

      result1.length shouldBe 5
      result2.length shouldBe 5
    }
  }

  "doobie" should {
    val file = File.newTemporaryFile()
    Migration.migrate(Some(file.pathAsString)).unsafeRunSync()
    val (doobie, closed) = ServiceStore.doobie(Some(file.pathAsString)).allocated.unsafeRunSync()
    check("doobie", doobie)
  }

  "quill" should {
    implicit val scheduler             = Scheduler.global
    implicit val options: Task.Options = Task.defaultOptions
    implicit val taskEff               = new CatsConcurrentEffectForTask
    val file = File.newTemporaryFile()
    Migration.migrate(Some(file.pathAsString)).unsafeRunSync()
    val (quill, closed) = ServiceStore.quill(Some(file.pathAsString)).allocated.unsafeRunSync()
    check("quill", quill)
  }
}
