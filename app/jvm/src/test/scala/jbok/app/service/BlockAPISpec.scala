package jbok.app.service

import cats.effect.IO
import jbok.app.AppSpec
import jbok.core.mining.BlockMiner
import jbok.common.testkit._
import jbok.core.ledger.History
import jbok.core.models.SignedTransaction
import jbok.core.testkit._
import jbok.core.api.BlockAPI

class BlockAPISpec extends AppSpec {
  "BlockAPI" should {
    "return bestBlockNumber correct" in {
      val objects = locator.unsafeRunSync()
      val miner   = objects.get[BlockMiner[IO]]
      val block   = objects.get[BlockAPI[IO]]

      miner.stream.take(40).compile.toList.unsafeRunSync()
      block.getBestBlockNumber.unsafeRunSync() shouldBe BigInt(40)
    }

    "return tx count in block" in {
      List(1024, 512).foreach { txCount =>
        val objects = locator.unsafeRunSync()
        val history = objects.get[History[IO]]
        val miner   = objects.get[BlockMiner[IO]]
        val block   = objects.get[BlockAPI[IO]]

        val txs    = random[List[SignedTransaction]](genTxs(txCount, txCount))
        val parent = history.getBestBlock.unsafeRunSync()
        miner.mine1(Some(parent), Some(txs)).unsafeRunSync()
        val bestBlock = history.getBestBlock.unsafeRunSync()
        // gasLimit in blockHeader limit the tx count in block
        if (txCount > 796) {
          block.getTransactionCountByHash(bestBlock.header.hash).unsafeRunSync().get shouldBe 796
        } else {
          block.getTransactionCountByHash(bestBlock.header.hash).unsafeRunSync().get shouldBe txCount
        }
      }
    }

    "return block by hash and number" in {
      val objects = locator.unsafeRunSync()
      val miner   = objects.get[BlockMiner[IO]]
      val block   = objects.get[BlockAPI[IO]]

      val minedBlock = miner.stream.take(1).compile.toList.unsafeRunSync().head
      block.getBestBlockNumber.unsafeRunSync() shouldBe BigInt(1)
      block.getBlockByNumber(BigInt(1)).unsafeRunSync().get shouldBe minedBlock.block
      block.getBlockByHash(minedBlock.block.header.hash).unsafeRunSync().get shouldBe minedBlock.block
    }
  }
}
