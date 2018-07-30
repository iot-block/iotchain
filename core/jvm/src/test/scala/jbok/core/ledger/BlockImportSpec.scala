package jbok.core.ledger

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.ledger.BlockImportResult.DuplicateBlock
import jbok.core.models.{Block, BlockBody, BlockHeader, Receipt}
import jbok.core.validators.Validators
import jbok.evm.VM
import scodec.bits.ByteVector

trait BlockImportFixture extends BlockPoolFixture {
  val validators = Validators(blockChain)
  val ledger = Ledger[IO](new VM, blockChain, blockChainConfig, validators, blockPool)

  val genesisHeader = defaultHeader.copy(number = 0, extraData = ByteVector("genesis".getBytes))

  def getChain(from: BigInt, to: BigInt, parent: ByteVector = randomHash()): List[Block] =
    if (from > to)
      Nil
    else {
      val block = getBlock(from, parent = parent)
      block :: getChain(from + 1, to, block.header.hash)
    }

  def getChainHeaders(from: BigInt, to: BigInt, parent: ByteVector = randomHash()): List[BlockHeader] =
    getChain(from, to, parent).map(_.header)

  val receipts = List(Receipt(randomHash(), 50000, randomHash(), Nil))

  val currentTd = 99999

  val bestNum = BigInt(5)

  override def getBlock(
      number: BigInt = 1,
      difficulty: BigInt = 100,
      parent: ByteVector = randomHash(),
      salt: ByteVector = randomHash(),
      ommers: List[BlockHeader] = Nil
  ): Block =
    Block(defaultHeader.copy(parentHash = parent, difficulty = difficulty, number = number, extraData = salt),
          BlockBody(Nil, ommers))

  val bestBlock = getBlock(bestNum, currentTd / 2)
}

class BlockImportSpec extends JbokSpec {
  "block import" should {
    "ignore existing block" in new BlockImportFixture {
      val block1 = getBlock()
      val block2 = getBlock()

      setBlockExists(block1, inChain = true, inQueue = false)
      ledger.importBlock(block1).unsafeRunSync() shouldEqual DuplicateBlock

      setBlockExists(block2, inChain = false, inQueue = true)
      ledger.importBlock(block2).unsafeRunSync() shouldEqual DuplicateBlock
    }

    "import a block to top of the main chain" in new BlockImportFixture {
      val block = getBlock(6, parent = bestBlock.header.hash)

      setBestBlock(bestBlock)
      setTotalDifficultyForBlock(bestBlock, currentTd)
//      ledger.setExecutionResult(block, Right(receipts))

//      (blockQueue.enqueueBlock _).expects(block, bestNum)
//        .returning(Some(Leaf(block.header.hash, currentTd + block.header.difficulty)))
//      (blockQueue.getBranch _).expects(block.header.hash, true).returning(List(block))

      val newTd = currentTd + block.header.difficulty
      blockChain.save(block, receipts, newTd, saveAsBestBlock = true)
      ledger.importBlock(block).unsafeRunSync() shouldBe BlockImportResult.BlockImported(List(block), List(newTd))
    }
  }
}
