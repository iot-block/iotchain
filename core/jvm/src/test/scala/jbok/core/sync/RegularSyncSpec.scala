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
          _ <- IO(Thread.sleep(1000))
          ommers <- regularSync.ommersPool.getOmmers(block.header.number)
          _ = println(ommers)
          _ <- stopAll
        } yield ()

        p.unsafeRunSync()
      }

      "handle pooled" in new RegularSyncFixture {
        val block = getBlock(1)

        val p = for {
          _ <- connect
          _ <- regularSync.start
          _ <- IO(Thread.sleep(1000))
          _ <- pm2.broadcast(NewBlock(block))
          _ <- IO(Thread.sleep(1000))
          b <- regularSync.ledger.blockPool.getBlockByHash(block.header.hash)
          _ = b shouldBe Some(block)
          _ <- stopAll
        } yield ()

        p.unsafeRunSync()
      }
    }

    "received NewBlockHash" should {
      "get block headers for unknown hashes" ignore {}

      "handle already known" ignore {}
    }

    "received BlockHeaders" should {}
  }
}
