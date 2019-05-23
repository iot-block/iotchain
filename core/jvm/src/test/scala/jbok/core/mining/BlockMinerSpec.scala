package jbok.core.mining

import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.implicits._
import jbok.common.testkit._
import jbok.core.{CoreSpec, NodeStatus}
import jbok.core.ledger.History
import jbok.core.models.SignedTransaction
import jbok.core.testkit._

class BlockMinerSpec extends CoreSpec {
  "BlockMiner" should {
    "mine block with no transaction" in check { objects =>
      val miner   = objects.get[BlockMiner[IO]]
      val history = objects.get[History[IO]]
      val parent  = history.getBestBlock.unsafeRunSync()
      miner.mine1(parent.some).map(_.isRight shouldBe true)
    }

    "mine block with transactions" in check { objects =>
      val miner   = objects.get[BlockMiner[IO]]
      val history = objects.get[History[IO]]
      val txs     = random[List[SignedTransaction]](genTxs(1, 1024))
      for {
        parent <- history.getBestBlock
        res    <- miner.mine1(parent.some, txs.some)
        _ = res.isRight shouldBe true
      } yield ()
    }

    "mine blocks" in check { objects =>
      val N       = 10
      val miner   = objects.get[BlockMiner[IO]]
      val status  = objects.get[Ref[IO, NodeStatus]]
      val history = objects.get[History[IO]]

      for {
        _   <- status.set(NodeStatus.Done)
        _   <- miner.stream.take(N).compile.toList
        res <- history.getBestBlockNumber
        _ = res shouldBe N
      } yield ()
    }
  }
}
