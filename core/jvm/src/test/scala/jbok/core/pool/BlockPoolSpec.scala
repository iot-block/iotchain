package jbok.core.pool

import cats.effect.IO
import cats.implicits._
import jbok.common.testkit._
import jbok.core.CoreSpec
import jbok.core.ledger.History
import jbok.core.mining.BlockMiner
import jbok.core.models.{Block, SignedTransaction}
import jbok.core.pool.BlockPool.Leaf
import jbok.core.testkit._

class BlockPoolSpec extends CoreSpec {
  "BlockPool" should {
    def randomTx = random[List[SignedTransaction]](genTxs(1, 1)).some

    "ignore block if it's already in" in check { objects =>
      val history = objects.get[History[IO]]
      val pool    = objects.get[BlockPool[IO]]
      val block   = random[List[Block]](genBlocks(1, 1)).head

      for {
        genesisDifficulty <- history.genesisHeader.map(_.difficulty)
        res               <- pool.addBlock(block)
        _ = res shouldBe Some(Leaf(block.header.hash, genesisDifficulty + block.header.difficulty))
        res <- pool.addBlock(block)
        _ = res shouldBe None
        res <- pool.contains(block.header.hash)
        _ = res shouldBe true
      } yield ()
    }

    "ignore blocks outside of range" in check { objects =>
      val history = objects.get[History[IO]]
      val pool    = objects.get[BlockPool[IO]]
      val blocks  = random[List[Block]](genBlocks(30, 30))
      for {
        _   <- history.putBestBlockNumber(15)
        _   <- pool.addBlock(blocks.head)
        res <- pool.contains(blocks.head.header.hash)
        _ = res shouldBe false
        _   <- pool.addBlock(blocks.last)
        res <- pool.contains(blocks.last.header.hash)
        _ = res shouldBe false
      } yield ()
    }

    "remove the blocks that fall out of range" in check { objects =>
      val history = objects.get[History[IO]]
      val pool    = objects.get[BlockPool[IO]]
      val blocks  = random[List[Block]](genBlocks(20, 20))

      for {
        _   <- pool.addBlock(blocks.head)
        res <- pool.contains(blocks.head.header.hash)
        _ = res shouldBe true
        _   <- history.putBestBlockNumber(20)
        _   <- pool.addBlock(blocks.last)
        res <- pool.contains(blocks.last.header.hash)
        _ = res shouldBe true
        res <- pool.contains(blocks.head.header.hash)
        _ = res shouldBe false
      } yield ()
    }

    "enqueue a block with queued ancestors rooted to the main chain updating its total difficulty" in check { objects =>
      val pool  = objects.get[BlockPool[IO]]
      val miner = objects.get[BlockMiner[IO]]

      for {
        block1  <- miner.mine().map(_.block)
        block2a <- miner.mine(block1.some, randomTx).map(_.block)
        block2b <- miner.mine(block1.some, randomTx).map(_.block)
        block3  <- miner.mine(block2a.some).map(_.block)
        _       <- pool.addBlock(block1)
        _       <- pool.addBlock(block2a)
        _       <- pool.addBlock(block2b)
        expectedTd = List(block1, block2a, block3).map(_.header.difficulty).sum
        res <- pool.addBlock(block3)
        _ = res shouldBe Some(Leaf(block3.header.hash, expectedTd))
      } yield ()
    }

    "pool an orphaned block" in check { objects =>
      val pool  = objects.get[BlockPool[IO]]
      val block = random[List[Block]](genBlocks(2, 2)).last

      for {
        res <- pool.addBlock(block)
        _ = res shouldBe None
        res <- pool.contains(block.header.hash)
        _ = res shouldBe true
      } yield ()
    }

    "remove a branch from a leaf up to the first shared ancestor" in check { objects =>
      val pool  = objects.get[BlockPool[IO]]
      val miner = objects.get[BlockMiner[IO]]

      for {
        block1  <- miner.mine().map(_.block)
        block2a <- miner.mine(block1.some, randomTx).map(_.block)
        block2b <- miner.mine(block1.some, randomTx).map(_.block)
        block2c <- miner.mine(block1.some).map(_.block)
        block3  <- miner.mine(block2a.some).map(_.block)
        _       <- pool.addBlock(block1)
        _       <- pool.addBlock(block2a)
        _       <- pool.addBlock(block2b)
        _       <- pool.addBlock(block3)
        res     <- pool.getBranch(block3.header.hash, delete = true)
        _ = res shouldBe List(block1, block2a, block3)
        res <- pool.contains(block3.header.hash)
        _ = res shouldBe false
        res <- pool.contains(block2a.header.hash)
        _ = res shouldBe false
      } yield ()
    }

    "remove a whole subtree down from an ancestor to all its leaves" in check { objects =>
      val pool    = objects.get[BlockPool[IO]]
      val miner   = objects.get[BlockMiner[IO]]
      val history = objects.get[History[IO]]
      val txs     = random[List[SignedTransaction]](genTxs(2, 2))

      for {
        genesis <- history.getBestBlock
        block1a <- miner.mine(genesis.some, txs.take(1).some).map(_.block)
        block1b <- miner.mine(genesis.some).map(_.block)
        block2a <- miner.mine(block1a.some, txs.takeRight(1).some).map(_.block)
        block2b <- miner.mine(block1a.some).map(_.block)
        block3  <- miner.mine(block2a.some).map(_.block)
        _       <- pool.addBlock(block1a)
        _       <- pool.addBlock(block1b)
        _       <- pool.addBlock(block2a)
        _       <- pool.addBlock(block2b)
        _       <- pool.addBlock(block3)
        _       <- pool.contains(block3.header.hash).map(_ shouldBe true)
        _       <- pool.contains(block2a.header.hash).map(_ shouldBe true)
        _       <- pool.contains(block2b.header.hash).map(_ shouldBe true)
        _       <- pool.contains(block1a.header.hash).map(_ shouldBe true)
        _       <- pool.contains(block1b.header.hash).map(_ shouldBe true)
        _       <- pool.removeSubtree(block1a.header.hash)
        _       <- pool.contains(block3.header.hash).map(_ shouldBe false)
        _       <- pool.contains(block2a.header.hash).map(_ shouldBe false)
        _       <- pool.contains(block2b.header.hash).map(_ shouldBe false)
        _       <- pool.contains(block1a.header.hash).map(_ shouldBe false)
        _       <- pool.contains(block1b.header.hash).map(_ shouldBe true)
      } yield ()

    }
  }
}
