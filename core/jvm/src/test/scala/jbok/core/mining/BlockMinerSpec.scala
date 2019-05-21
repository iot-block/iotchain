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
    "mine block with no transaction" in {
      val objects = locator.unsafeRunSync()
      val miner   = objects.get[BlockMiner[IO]]
      val history = objects.get[History[IO]]
      val parent  = history.getBestBlock.unsafeRunSync()
      miner.mine1(parent.some).unsafeRunSync().isRight shouldBe true
    }

    "mine block with transactions" in {
      val objects = locator.unsafeRunSync()
      val miner   = objects.get[BlockMiner[IO]]
      val history = objects.get[History[IO]]
      val txs     = random[List[SignedTransaction]](genTxs(1, 1024))
      val parent  = history.getBestBlock.unsafeRunSync()
      miner.mine1(parent.some, txs.some).unsafeRunSync().isRight shouldBe true
    }

    "mine blocks" in {
      val N       = 10
      val objects = locator.unsafeRunSync()
      val miner   = objects.get[BlockMiner[IO]]
      val status  = objects.get[Ref[IO, NodeStatus]]
      status.set(NodeStatus.Done).unsafeRunSync()
      val history = objects.get[History[IO]]
      miner.stream.take(N).compile.toList.unsafeRunSync()
      history.getBestBlockNumber.unsafeRunSync() shouldBe N
    }
  }
}
