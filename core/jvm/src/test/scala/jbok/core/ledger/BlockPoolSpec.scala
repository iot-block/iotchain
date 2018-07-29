package jbok.core.ledger

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.BlockChainFixture
import jbok.core.ledger.BlockPool.Leaf
import jbok.core.models.{Block, BlockBody, BlockHeader}
import jbok.testkit.Gens
import scodec.bits.ByteVector

trait BlockPoolFixture extends BlockChainFixture {
  val blockPool = BlockPool[IO](blockChain, 10, 10).unsafeRunSync()

  def setBestBlockNumber(n: BigInt) =
    blockChain.setBestBlockNumber(n).unsafeRunSync()

  def setTotalDifficultyForParent(block: Block, td: BigInt) =
    blockChain.save(block.header.parentHash, td).unsafeRunSync()

  def setBlockExists(block: Block, inChain: Boolean, inQueue: Boolean) =
    if (inChain) {
      blockChain.save(block).unsafeRunSync()
    } else if (inQueue) {
      blockPool.addBlock(block, 1).unsafeRunSync()
    } else {
      ()
    }

  def setBestBlock(block: Block) = {
    blockChain.save(block).unsafeRunSync()
    blockChain.setBestBlockNumber(block.header.number).unsafeRunSync()
  }

  def setTotalDifficultyForBlock(block: Block, td: BigInt) =
    blockChain.save(block.header.hash, td).unsafeRunSync()

//  def expectBlockSaved(block: Block, receipts: Seq[Receipt], td: BigInt, saveAsBestBlock: Boolean) = {
//    (blockchain.save(_: Block, _: Seq[Receipt], _: BigInt, _: Boolean))
//      .expects(block, receipts, td, saveAsBestBlock).once()
//  }
//
//  def setHeaderByHash(hash: ByteString, header: Option[BlockHeader]) =
//    (blockchain.getBlockHeaderByHash _).expects(hash).returning(header)
//
//  def setBlockByNumber(number: BigInt, block: Option[Block]) =
//    (blockchain.getBlockByNumber _).expects(number).returning(block)
//
//  def setGenesisHeader(header: BlockHeader) = {
//    (blockchain.getBlockHeaderByNumber _).expects(BigInt(0)).returning(Some(header))
//    setHeaderByHash(header.parentHash, None)
//  }

  def randomHash() = Gens.byteStringOfLengthNGen(32).sample.get

  val defaultHeader = BlockHeader(
    parentHash = ByteVector.empty,
    ommersHash = ByteVector.empty,
    beneficiary = ByteVector.empty,
    stateRoot = ByteVector.empty,
    transactionsRoot = ByteVector.empty,
    receiptsRoot = ByteVector.empty,
    logsBloom = ByteVector.empty,
    difficulty = 1000000,
    number = 1,
    gasLimit = 1000000,
    gasUsed = 0,
    unixTimestamp = 0,
    extraData = ByteVector.empty,
    mixHash = ByteVector.empty,
    nonce = ByteVector.empty
  )

  def getBlock(
      number: BigInt,
      difficulty: BigInt = 1000000,
      parent: ByteVector = randomHash(),
      salt: ByteVector = randomHash(),
      ommers: List[BlockHeader] = Nil
  ): Block =
    Block(
      defaultHeader.copy(parentHash = parent, difficulty = difficulty, number = number, extraData = salt),
      BlockBody(Nil, ommers)
    )
}

class BlockPoolSpec extends JbokSpec {
  "BlockPool" should {
    "ignore block if it's already in" in new BlockPoolFixture {
      val block = getBlock(1)
      setBestBlockNumber(1)
      setTotalDifficultyForParent(block, 0)

      println(blockChain.getTotalDifficultyByHash(block.header.parentHash).unsafeRunSync())

      blockPool.addBlock(block, 1).unsafeRunSync() shouldEqual Some(Leaf(block.header.hash, block.header.difficulty))
      blockPool.addBlock(block, 1).unsafeRunSync() shouldEqual None
      blockPool.contains(block.header.hash).unsafeRunSync() shouldBe true
    }
  }

  "ignore blocks outside of range" in new BlockPoolFixture {
    val block1 = getBlock(1)
    val block30 = getBlock(30)
    setBestBlockNumber(15)

    blockPool.addBlock(block1, 15).unsafeRunSync()
    blockPool.contains(block1.header.hash).unsafeRunSync() shouldBe false

    blockPool.addBlock(block30, 15).unsafeRunSync()
    blockPool.contains(block30.header.hash).unsafeRunSync() shouldBe false
  }

  "remove the blocks that fall out of range" in new BlockPoolFixture {
    val block1 = getBlock(1)
    setBestBlockNumber(1)

    blockPool.addBlock(block1, 1).unsafeRunSync()
    blockPool.contains(block1.header.hash).unsafeRunSync() shouldBe true

    val block20 = getBlock(20)
    setBestBlockNumber(20)

    blockPool.addBlock(block20, 20).unsafeRunSync()
    blockPool.contains(block20.header.hash).unsafeRunSync() shouldBe true
    blockPool.contains(block1.header.hash).unsafeRunSync() shouldBe false
  }

  "enqueue a block with parent on the main chain updating its total difficulty" in new BlockPoolFixture {
    val block1 = getBlock(1, 13)
    setBestBlockNumber(1)
    setTotalDifficultyForParent(block1, 42)

    blockPool.addBlock(block1, 1).unsafeRunSync() shouldBe Some(Leaf(block1.header.hash, block1.header.difficulty + 42))
  }

  "enqueue a block with queued ancestors rooted to the main chain updating its total difficulty" in new BlockPoolFixture {
    val block1 = getBlock(1, 101)
    val block2a = getBlock(2, 102, block1.header.hash)
    val block2b = getBlock(2, 99, block1.header.hash)
    val block3 = getBlock(3, 103, block2a.header.hash)

    setBestBlockNumber(1)
    setTotalDifficultyForParent(block1, 42)

    blockPool.addBlock(block1, 1).unsafeRunSync()
    blockPool.addBlock(block2a, 1).unsafeRunSync()
    blockPool.addBlock(block2b, 1).unsafeRunSync()

    val expectedTd = 42 + List(block1, block2a, block3).map(_.header.difficulty).sum
    blockPool.addBlock(block3, 1).unsafeRunSync() shouldBe Some(Leaf(block3.header.hash, expectedTd))
  }

  "enqueue an orphaned block" in new BlockPoolFixture {
    val block1 = getBlock(1)
    setBestBlockNumber(1)

    blockPool.addBlock(block1, 1).unsafeRunSync() shouldBe None
    blockPool.contains(block1.header.hash).unsafeRunSync() shouldBe true
  }

  "remove a branch from a leaf up to the first shared ancestor" in new BlockPoolFixture {
    val block1 = getBlock(1)
    val block2a = getBlock(2, parent = block1.header.hash)
    val block2b = getBlock(2, parent = block1.header.hash)
    val block3 = getBlock(3, parent = block2a.header.hash)

    setBestBlockNumber(1)

    blockPool.addBlock(block1, 1).unsafeRunSync()
    blockPool.addBlock(block2a, 1).unsafeRunSync()
    blockPool.addBlock(block2b, 1).unsafeRunSync()
    blockPool.addBlock(block3, 1).unsafeRunSync()

    blockPool.getBranch(block3.header.hash, dequeue = true).unsafeRunSync() shouldBe List(block1, block2a, block3)

    blockPool.contains(block3.header.hash).unsafeRunSync() shouldBe false
    blockPool.contains(block2a.header.hash).unsafeRunSync() shouldBe false
    blockPool.contains(block2b.header.hash).unsafeRunSync() shouldBe true
    blockPool.contains(block1.header.hash).unsafeRunSync() shouldBe true
  }

  "remove a whole subtree down from an ancestor to all its leaves" in new BlockPoolFixture {
    val block1a = getBlock(1)
    val block1b = getBlock(1)
    val block2a = getBlock(2, parent = block1a.header.hash)
    val block2b = getBlock(2, parent = block1a.header.hash)
    val block3 = getBlock(3, parent = block2a.header.hash)

    setBestBlockNumber(1)

    blockPool.addBlock(block1a, 1).unsafeRunSync()
    blockPool.addBlock(block1b, 1).unsafeRunSync()
    blockPool.addBlock(block2a, 1).unsafeRunSync()
    blockPool.addBlock(block2b, 1).unsafeRunSync()
    blockPool.addBlock(block3, 1).unsafeRunSync()

    blockPool.contains(block3.header.hash).unsafeRunSync() shouldBe true
    blockPool.contains(block2a.header.hash).unsafeRunSync() shouldBe true
    blockPool.contains(block2b.header.hash).unsafeRunSync() shouldBe true
    blockPool.contains(block1a.header.hash).unsafeRunSync() shouldBe true
    blockPool.contains(block1b.header.hash).unsafeRunSync() shouldBe true

    blockPool.removeSubtree(block1a.header.hash).unsafeRunSync()

    blockPool.contains(block3.header.hash).unsafeRunSync() shouldBe false
    blockPool.contains(block2a.header.hash).unsafeRunSync() shouldBe false
    blockPool.contains(block2b.header.hash).unsafeRunSync() shouldBe false
    blockPool.contains(block1a.header.hash).unsafeRunSync() shouldBe false
    blockPool.contains(block1b.header.hash).unsafeRunSync() shouldBe true
  }
}
