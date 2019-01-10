package jbok.app

import cats.effect.IO
import doobie.implicits._
import jbok.JbokSpec
import jbok.app.ScanServiceStore.DDL
import jbok.common.execution._
import jbok.common.testkit._
import jbok.core.ledger.BlockExecutor
import jbok.core.models.{Block, SignedTransaction}
import jbok.core.testkit._

class ScanServiceStoreSpec extends JbokSpec {
  "ScanServiceStore" should {
    DDL.dropTable.transact(ScanServiceStore.xa).unsafeRunSync()
    DDL.createTable.transact(ScanServiceStore.xa).unsafeRunSync()
    DDL.createIndex.transact(ScanServiceStore.xa).unsafeRunSync()

    "insert values" in {
      implicit val config = testConfig
      val executor        = random[BlockExecutor[IO]]
      val txs             = random[List[SignedTransaction]](genTxs(min = 10, max = 10))
      val block           = random[Block](genBlock(stxsOpt = Some(txs)))
      val executed        = executor.executeBlock(block).unsafeRunSync()

      ScanServiceStore.insert(executed.block, executed.receipts).unsafeRunSync()

      val result1 = ScanServiceStore
        .select(txs.head.senderAddress.get, 0, None, 1, 5)
        .unsafeRunSync()

      result1.length shouldBe 5

      val result2 = ScanServiceStore
        .select(txs.head.senderAddress.get, 0, None, 2, 5)
        .unsafeRunSync()

      result1.length shouldBe 5
      result2.length shouldBe 5
    }
  }
}
