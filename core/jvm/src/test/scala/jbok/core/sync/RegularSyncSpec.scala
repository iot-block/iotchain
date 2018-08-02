package jbok.core.sync

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.TxPoolFixture
import jbok.core.ledger.{BlockImportFixture, OmmersPool}
import jbok.core.messages.NewBlock
import jbok.network.execution._

trait RegularSyncFixture extends BlockImportFixture with TxPoolFixture with BroadcasterFixture {
  val ommersPool = OmmersPool[IO](blockChain).unsafeRunSync()
  val regularSync = RegularSync[IO](pm1, ledger, ommersPool, txPool, broadcaster).unsafeRunSync()
}

class RegularSyncSpec extends JbokSpec {
  "regular sync" when {
    "received NewBlock" should {
      "import block to the main chain" in new RegularSyncFixture {
        val block = getBlock(6, parent = bestBlock.header.hash)
        setBestBlock(bestBlock)
        setTotalDifficultyForBlock(bestBlock, currentTd)

        val p = for {
          _ <- connect
          _ <- regularSync.start
          _ <- pm2.broadcast(NewBlock(block))
          ommers <- regularSync.ommersPool.getOmmers(block.header.number)
          _ = println(ommers)
        } yield ()

        p.attempt.unsafeRunSync()
        stopAll.unsafeRunSync()
      }

      "handle pooled" in new RegularSyncFixture {
        val block = getBlock(1)

        val p = for {
          _ <- connect
          _ <- regularSync.start
          _ <- pm2.broadcast(NewBlock(block))
          b <- regularSync.ledger.blockPool.getBlockByHash(block.header.hash)
          _ = b shouldBe Some(block)
        } yield ()

        p.attempt.unsafeRunSync()
        stopAll.unsafeRunSync()
      }
    }

    "received NewBlockHash" should {
      "get block headers for unknown hashes" ignore {}

      "handle already known" ignore {}
    }

    "received BlockHeaders" should {}
  }
}
