//package jbok.core.pool
//
//import cats.effect.IO
//import cats.implicits._
//import jbok.common.testkit._
//import jbok.core.CoreSpec
//import jbok.core.ledger.History
//import jbok.core.mining.BlockMiner
//import jbok.core.models.{Block, SignedTransaction}
//import jbok.core.pool.BlockPool.Leaf
//import jbok.core.testkit._
//
//class BlockPoolSpec extends CoreSpec {
//  "BlockPool" should {
//    def randomTx = random[List[SignedTransaction]](genTxs(1, 1)).some
//
//    "ignore block if it's already in" in {
//      val objects           = locator.unsafeRunSync()
//      val history           = objects.get[History[IO]]
//      val pool              = objects.get[BlockPool[IO]]
//      val block             = random[List[Block]](genBlocks(1, 1)).head
//      val genesisDifficulty = history.genesisHeader.unsafeRunSync().difficulty
//
//      pool.addBlock(block).unsafeRunSync() shouldBe Some(Leaf(block.header.hash, genesisDifficulty + block.header.difficulty))
//      pool.addBlock(block).unsafeRunSync() shouldBe None
//      pool.contains(block.header.hash).unsafeRunSync() shouldBe true
//    }
//
//    "ignore blocks outside of range" in {
//      val objects = locator.unsafeRunSync()
//      val history = objects.get[History[IO]]
//      val pool    = objects.get[BlockPool[IO]]
//      val blocks  = random[List[Block]](genBlocks(30, 30))
//      history.putBestBlockNumber(15).unsafeRunSync()
//      pool.addBlock(blocks.head).unsafeRunSync()
//      pool.contains(blocks.head.header.hash).unsafeRunSync() shouldBe false
//
//      pool.addBlock(blocks.last).unsafeRunSync()
//      pool.contains(blocks.last.header.hash).unsafeRunSync() shouldBe false
//    }
//
//    "remove the blocks that fall out of range" in {
//      val objects = locator.unsafeRunSync()
//      val history = objects.get[History[IO]]
//      val pool    = objects.get[BlockPool[IO]]
//      val blocks  = random[List[Block]](genBlocks(20, 20))
//
//      pool.addBlock(blocks.head).unsafeRunSync()
//      pool.contains(blocks.head.header.hash).unsafeRunSync() shouldBe true
//
//      history.putBestBlockNumber(20).unsafeRunSync()
//
//      pool.addBlock(blocks.last).unsafeRunSync()
//      pool.contains(blocks.last.header.hash).unsafeRunSync() shouldBe true
//      pool.contains(blocks.head.header.hash).unsafeRunSync() shouldBe false
//    }
//
//    "enqueue a block with queued ancestors rooted to the main chain updating its total difficulty" in {
//      val objects = locator.unsafeRunSync()
//      val pool    = objects.get[BlockPool[IO]]
//      val miner   = objects.get[BlockMiner[IO]]
//      val block1  = miner.mine1().unsafeRunSync().right.get.block
//      val block2a = miner.mine1(block1.some, randomTx).unsafeRunSync().right.get.block
//      val block2b = miner.mine1(block1.some, randomTx).unsafeRunSync().right.get.block
//      val block3  = miner.mine1(block2a.some).unsafeRunSync().right.get.block
//
//      pool.addBlock(block1).unsafeRunSync()
//      pool.addBlock(block2a).unsafeRunSync()
//      pool.addBlock(block2b).unsafeRunSync()
//
//      val expectedTd = 1024 + List(block1, block2a, block3).map(_.header.difficulty).sum
//      pool.addBlock(block3).unsafeRunSync() shouldBe Some(Leaf(block3.header.hash, expectedTd))
//    }
//
//    "pool an orphaned block" in {
//      val objects = locator.unsafeRunSync()
//      val pool    = objects.get[BlockPool[IO]]
//      val block   = random[List[Block]](genBlocks(2, 2)).last
//
//      pool.addBlock(block).unsafeRunSync() shouldBe None
//      pool.contains(block.header.hash).unsafeRunSync() shouldBe true
//    }
//
//    "remove a branch from a leaf up to the first shared ancestor" in {
//      val objects = locator.unsafeRunSync()
//      val pool    = objects.get[BlockPool[IO]]
//      val miner   = objects.get[BlockMiner[IO]]
//      val block1  = miner.mine1().unsafeRunSync().block
//      val block2a = miner.mine1(block1.some, randomTx).unsafeRunSync().block
//      val block2b = miner.mine1(block1.some, randomTx).unsafeRunSync().block
//      val block2c = miner.mine1(block1.some).unsafeRunSync().block
//      val block3  = miner.mine1(block2a.some).unsafeRunSync().block
//
//      pool.addBlock(block1).unsafeRunSync()
//      pool.addBlock(block2a).unsafeRunSync()
//      pool.addBlock(block2b).unsafeRunSync()
//      pool.addBlock(block3).unsafeRunSync()
//
//      pool.getBranch(block3.header.hash, delete = true).unsafeRunSync() shouldBe List(block1, block2a, block3)
//
//      pool.contains(block3.header.hash).unsafeRunSync() shouldBe false
//      pool.contains(block2a.header.hash).unsafeRunSync() shouldBe false
//      pool.contains(block2b.header.hash).unsafeRunSync() shouldBe true
//      pool.contains(block1.header.hash).unsafeRunSync() shouldBe true
//    }
//
//    "remove a whole subtree down from an ancestor to all its leaves" in {
//      val objects = locator.unsafeRunSync()
//      val pool    = objects.get[BlockPool[IO]]
//      val miner   = objects.get[BlockMiner[IO]]
//      val history = objects.get[History[IO]]
//      val genesis = history.getBestBlock.unsafeRunSync()
//      val txs     = random[List[SignedTransaction]](genTxs(2, 2))
//
//      val block1a = miner.mine1(genesis.some, txs.take(1).some).unsafeRunSync().block
//      val block1b = miner.mine1(genesis.some).unsafeRunSync().block
//      val block2a = miner.mine1(block1a.some, txs.takeRight(1).some).unsafeRunSync().block
//      val block2b = miner.mine1(block1a.some).unsafeRunSync().block
//      val block3  = miner.mine1(block2a.some).unsafeRunSync().block
//
//      pool.addBlock(block1a).unsafeRunSync()
//      pool.addBlock(block1b).unsafeRunSync()
//      pool.addBlock(block2a).unsafeRunSync()
//      pool.addBlock(block2b).unsafeRunSync()
//      pool.addBlock(block3).unsafeRunSync()
//
//      pool.contains(block3.header.hash).unsafeRunSync() shouldBe true
//      pool.contains(block2a.header.hash).unsafeRunSync() shouldBe true
//      pool.contains(block2b.header.hash).unsafeRunSync() shouldBe true
//      pool.contains(block1a.header.hash).unsafeRunSync() shouldBe true
//      pool.contains(block1b.header.hash).unsafeRunSync() shouldBe true
//
//      pool.removeSubtree(block1a.header.hash).unsafeRunSync()
//
//      pool.contains(block3.header.hash).unsafeRunSync() shouldBe false
//      pool.contains(block2a.header.hash).unsafeRunSync() shouldBe false
//      pool.contains(block2b.header.hash).unsafeRunSync() shouldBe false
//      pool.contains(block1a.header.hash).unsafeRunSync() shouldBe false
//      pool.contains(block1b.header.hash).unsafeRunSync() shouldBe true
//    }
//  }
//}
