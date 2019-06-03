package jbok.app.service

import cats.effect.IO
import jbok.app.AppSpec
import jbok.common.math.N
import jbok.core.StatefulGen
import jbok.core.api.BlockAPI
import jbok.core.ledger.History
import jbok.core.mining.BlockMiner

class BlockAPISpec extends AppSpec {
  "BlockAPI" should {
    "return bestBlockNumber correct" in check { objects =>
      val miner  = objects.get[BlockMiner[IO]]
      val block  = objects.get[BlockAPI[IO]]

      for {
        _   <- miner.mineN(40)
        res <- block.getBestBlockNumber
        _ = res shouldBe N(40)
      } yield ()
    }

    "return tx count in block" in {
      List(1024, 512).foreach { txCount =>
        check { objects =>
          val history = objects.get[History[IO]]
          val miner   = objects.get[BlockMiner[IO]]
          val block   = objects.get[BlockAPI[IO]]
          val txs     = random(StatefulGen.transactions(txCount, txCount))

          for {
            parent    <- history.getBestBlock
            _         <- miner.mine(Some(parent), Some(txs))
            bestBlock <- history.getBestBlock
            // gasLimit in blockHeader limit the tx count in block
            _ <- if (txCount > 796) {
              block.getTransactionCountByHash(bestBlock.header.hash).map(_.get shouldBe 796)
            } else {
              block.getTransactionCountByHash(bestBlock.header.hash).map(_.get shouldBe txCount)
            }
          } yield ()
        }
      }
    }

    "return block by hash and number" in check { objects =>
      val miner = objects.get[BlockMiner[IO]]
      val block = objects.get[BlockAPI[IO]]

      for {
        minedBlock <- miner.mine()
        res               <- block.getBestBlockNumber
        _ = res shouldBe N(1)
        res <- block.getBlockByNumber(N(1)).map(_.get)
        _ = res shouldBe minedBlock.block
        res <- block.getBlockByHash(minedBlock.block.header.hash).map(_.get)
        _ = res shouldBe minedBlock.block
      } yield ()
    }
  }
}
