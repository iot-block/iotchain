package jbok.core.pool

import cats.effect.IO
import cats.implicits._
import jbok.JbokSpec
import jbok.common.testkit._
import jbok.core.mining.BlockMiner
import jbok.core.models.{Block, SignedTransaction}
import jbok.core.pool.BlockPool.Leaf
import jbok.core.testkit._

class BlockPoolSpec extends JbokSpec {
  "BlockPool" should {
    implicit val fixture = defaultFixture()

    "ignore block if it's already in" in {
      val pool              = random[BlockPool[IO]]
      val block             = random[List[Block]](genBlocks(1, 1)).head
      val genesisDifficulty = pool.history.genesisHeader.unsafeRunSync().difficulty

      pool.addBlock(block, 1).unsafeRunSync() shouldBe Some(
        Leaf(block.header.hash, genesisDifficulty + block.header.difficulty))
      pool.addBlock(block, 1).unsafeRunSync() shouldBe None
      pool.contains(block.header.hash).unsafeRunSync() shouldBe true
    }

    "ignore blocks outside of range" in {
      val pool   = random[BlockPool[IO]]
      val blocks = random[List[Block]](genBlocks(30, 30))
      pool.history.putBestBlockNumber(15).unsafeRunSync()
      pool.addBlock(blocks.head, 15).unsafeRunSync()
      pool.contains(blocks.head.header.hash).unsafeRunSync() shouldBe false

      pool.addBlock(blocks.last, 15).unsafeRunSync()
      pool.contains(blocks.last.header.hash).unsafeRunSync() shouldBe false
    }

    "remove the blocks that fall out of range" in {
      val pool   = random[BlockPool[IO]]
      val blocks = random[List[Block]](genBlocks(20, 20))

      pool.addBlock(blocks.head, 1).unsafeRunSync()
      pool.contains(blocks.head.header.hash).unsafeRunSync() shouldBe true

      pool.history.putBestBlockNumber(20)

      pool.addBlock(blocks.last, 20).unsafeRunSync()
      pool.contains(blocks.last.header.hash).unsafeRunSync() shouldBe true
      pool.contains(blocks.head.header.hash).unsafeRunSync() shouldBe false
    }

    "enqueue a block with queued ancestors rooted to the main chain updating its total difficulty" in {
      val pool    = random[BlockPool[IO]]
      val miner   = random[BlockMiner[IO]]
      val block1  = miner.mine1().unsafeRunSync().block
      val block2a = miner.mine1(block1.some).unsafeRunSync().block
      val block2b = miner.mine1(block1.some).unsafeRunSync().block
      val block3  = miner.mine1(block2a.some).unsafeRunSync().block

      pool.addBlock(block1, 1).unsafeRunSync()
      pool.addBlock(block2a, 1).unsafeRunSync()
      pool.addBlock(block2b, 1).unsafeRunSync()

      val expectedTd = 1024 + List(block1, block2a, block3).map(_.header.difficulty).sum
      pool.addBlock(block3, 1).unsafeRunSync() shouldBe Some(Leaf(block3.header.hash, expectedTd))
    }

    "pool an orphaned block" in {
      val pool  = random[BlockPool[IO]]
      val block = random[List[Block]](genBlocks(2, 2)).last

      pool.addBlock(block, 1).unsafeRunSync() shouldBe None
      pool.contains(block.header.hash).unsafeRunSync() shouldBe true
    }

    "remove a branch from a leaf up to the first shared ancestor" in {
      val pool  = random[BlockPool[IO]]
      val miner = random[BlockMiner[IO]]
      val txs   = random[List[SignedTransaction]](genTxs(2, 2))

      val block1 = miner.mine1().unsafeRunSync().block

      val block2a = miner.mine1(block1.some, txs.take(1).some).unsafeRunSync().block
      val block2b = miner.mine1(block1.some, txs.takeRight(1).some).unsafeRunSync().block
      val block2c = miner.mine1(block1.some).unsafeRunSync().block
      val block3  = miner.mine1(block2a.some).unsafeRunSync().block

      pool.addBlock(block1, 1).unsafeRunSync()
      pool.addBlock(block2a, 1).unsafeRunSync()
      pool.addBlock(block2b, 1).unsafeRunSync()
      pool.addBlock(block3, 1).unsafeRunSync()

      pool.getBranch(block3.header.hash, dequeue = true).unsafeRunSync() shouldBe List(block1, block2a, block3)

      pool.contains(block3.header.hash).unsafeRunSync() shouldBe false
      pool.contains(block2a.header.hash).unsafeRunSync() shouldBe false
      pool.contains(block2b.header.hash).unsafeRunSync() shouldBe true
      pool.contains(block1.header.hash).unsafeRunSync() shouldBe true
    }

    "remove a whole subtree down from an ancestor to all its leaves" in {
      val pool     = random[BlockPool[IO]]
      val miner    = random[BlockMiner[IO]]
      val genesis  = miner.history.getBestBlock.unsafeRunSync()
      def randomTx = random[List[SignedTransaction]](genTxs(1, 1)).some
      val block1a  = miner.mine1(genesis.some, randomTx).unsafeRunSync().block
      val block1b  = miner.mine1(genesis.some, randomTx).unsafeRunSync().block
      val block2a  = miner.mine1(block1a.some, randomTx).unsafeRunSync().block
      val block2b  = miner.mine1(block1a.some, randomTx).unsafeRunSync().block
      val block3   = miner.mine1(block2a.some, randomTx).unsafeRunSync().block

      pool.addBlock(block1a, 1).unsafeRunSync()
      pool.addBlock(block1b, 1).unsafeRunSync()
      pool.addBlock(block2a, 1).unsafeRunSync()
      pool.addBlock(block2b, 1).unsafeRunSync()
      pool.addBlock(block3, 1).unsafeRunSync()

      pool.contains(block3.header.hash).unsafeRunSync() shouldBe true
      pool.contains(block2a.header.hash).unsafeRunSync() shouldBe true
      pool.contains(block2b.header.hash).unsafeRunSync() shouldBe true
      pool.contains(block1a.header.hash).unsafeRunSync() shouldBe true
      pool.contains(block1b.header.hash).unsafeRunSync() shouldBe true

      pool.removeSubtree(block1a.header.hash).unsafeRunSync()

      pool.contains(block3.header.hash).unsafeRunSync() shouldBe false
      pool.contains(block2a.header.hash).unsafeRunSync() shouldBe false
      pool.contains(block2b.header.hash).unsafeRunSync() shouldBe false
      pool.contains(block1a.header.hash).unsafeRunSync() shouldBe false
      pool.contains(block1b.header.hash).unsafeRunSync() shouldBe true
    }
  }
}
