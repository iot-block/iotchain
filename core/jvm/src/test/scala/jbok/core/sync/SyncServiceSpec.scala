package jbok.core.sync

import cats.effect.IO
import jbok.common.testkit._
import jbok.core.CoreSpec
import jbok.core.ledger.History
import jbok.core.messages.{BlockBodies, BlockHeaders}
import jbok.core.models.{Block, BlockHeader}
import jbok.core.testkit._

class SyncServiceSpec extends CoreSpec {
  "SyncService" should {
    "return BlockBodies by block hashes" in {
      val objects      = locator.unsafeRunSync()
      val history      = objects.get[History[IO]]
      val blockService = objects.get[SyncService[IO]]
      forAll { block: Block =>
        history.putBlockBody(block.header.hash, block.body).unsafeRunSync()
        blockService.getBlockBodies(List(block.header.hash)).unsafeRunSync() shouldBe BlockBodies(List(block.body))
      }
    }

    "return BlockHeaders by block number/hash" in {
      val objects      = locator.unsafeRunSync()
      val history      = objects.get[History[IO]]
      val blockService = objects.get[SyncService[IO]]

      val baseHeader: BlockHeader   = random[BlockHeader]
      val firstHeader: BlockHeader  = baseHeader.copy(number = 3)
      val secondHeader: BlockHeader = baseHeader.copy(number = 4)

      history.putBlockHeader(firstHeader).unsafeRunSync()
      history.putBlockHeader(secondHeader).unsafeRunSync()
      history.putBlockHeader(baseHeader.copy(number = 5)).unsafeRunSync()
      history.putBlockHeader(baseHeader.copy(number = 6)).unsafeRunSync()
      blockService.getBlockHeadersByNumber(3, 2).unsafeRunSync() shouldBe BlockHeaders(List(firstHeader, secondHeader))
      blockService.getBlockHeadersByHash(firstHeader.hash, 2).unsafeRunSync() shouldBe BlockHeaders(List(firstHeader, secondHeader))
    }
  }
}
