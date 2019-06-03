package jbok.core.mining

import cats.effect.IO
import cats.implicits._
import jbok.core.{CoreSpec, StatefulGen}
import jbok.core.ledger.History
import jbok.core.models.SignedTransaction

class BlockMinerSpec extends CoreSpec {
  "BlockMiner" should {
    "mine block with no transaction" in check { objects =>
      val miner   = objects.get[BlockMiner[IO]]
      val history = objects.get[History[IO]]
      val parent  = history.getBestBlock.unsafeRunSync()


      miner.mine(parent.some).attempt.map(_.isRight shouldBe true).void
    }

    "mine block with transactions" in check { objects =>
      val miner   = objects.get[BlockMiner[IO]]
      val history = objects.get[History[IO]]
      val txs     = random[List[SignedTransaction]](StatefulGen.transactions(1, 1024, history))
      for {
        parent <- history.getBestBlock
        _      <- miner.mine(parent.some, txs.some)
      } yield ()
    }

    "mine blocks" in check { objects =>
      val N       = 10
      val miner   = objects.get[BlockMiner[IO]]
      val history = objects.get[History[IO]]

      for {
        _   <- miner.mineN(N)
        res <- history.getBestBlockNumber
        _ = res shouldBe N
      } yield ()
    }
  }
}
