package jbok.core.mining

import cats.effect.IO
import cats.implicits._
import jbok.JbokSpec
import jbok.common.testkit._
import jbok.core.models.SignedTransaction
import jbok.core.testkit._

class BlockMinerSpec extends JbokSpec {
  implicit val fixture = defaultFixture()

  "BlockMiner" should {
    "mine block with no transaction" in {
      val miner  = random[BlockMiner[IO]]
      val parent = miner.history.getBestBlock.unsafeRunSync()
      miner.mine1(parent.some).unsafeRunSync()
    }

    "mine block with transactions" in {
      val miner = random[BlockMiner[IO]]
      val txs   = random[List[SignedTransaction]](genTxs(1, 1024))
      println(txs.length)
      val parent = miner.history.getBestBlock.unsafeRunSync()
      miner.mine1(parent.some, txs.some).unsafeRunSync()
    }

    "mine blocks" in {
      val N     = 10
      val miner = random[BlockMiner[IO]]
      miner.stream.take(N).compile.toList.unsafeRunSync()
      miner.history.getBestBlockNumber.unsafeRunSync() shouldBe N
    }
  }
}
