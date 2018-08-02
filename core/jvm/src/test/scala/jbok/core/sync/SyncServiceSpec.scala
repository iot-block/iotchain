package jbok.core.sync

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.PeerManageFixture
import jbok.core.messages._
import jbok.core.models.{BlockBody, BlockHeader}
import jbok.network.execution._
import scodec.bits._
import scala.concurrent.duration._

trait SyncServiceFixture extends PeerManageFixture {
  val blockchain = pm1.blockchain
  val syncService: SyncService[IO] =
    SyncService[IO](pm1, blockchain).unsafeRunSync()
}

class SyncServiceSpec extends JbokSpec {
  "sync service" should {
    "return Receipts by block hashes" in new SyncServiceFixture {

      val receiptsHashes = List(
        hex"a218e2c611f21232d857e3c8cecdcdf1f65f25a4477f98f6f47e4063807f2308",
        hex"1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347"
      )

      val receipts: Receipts = Receipts(List(Nil, Nil))

      val p = for {
        _ <- connect
        _ <- syncService.start
        _ <- blockchain.save(receiptsHashes(0), Nil)
        _ <- blockchain.save(receiptsHashes(1), Nil)
        _ <- pm2.broadcast(GetReceipts(receiptsHashes))
        x <- pm2.subscribeMessages().take(1).compile.toList
        _ = x.head.message shouldBe receipts
      } yield ()

      p.attempt.unsafeRunTimed(5.seconds)
      stopAll.unsafeRunSync()
    }

    "return BlockBodies by block hashes" in new SyncServiceFixture {
      val blockBodiesHashes = List(
        hex"a218e2c611f21232d857e3c8cecdcdf1f65f25a4477f98f6f47e4063807f2308",
        hex"1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347"
      )

      val body = BlockBody(Nil, Nil)
      val blockBodies = List(body, body)

      val p = for {
        _ <- connect
        _ <- syncService.start
        _ <- blockchain.save(blockBodiesHashes(0), blockBodies(0))
        _ <- blockchain.save(blockBodiesHashes(1), blockBodies(1))
        _ <- pm2.broadcast(GetBlockBodies(blockBodiesHashes))
        x <- pm2.subscribeMessages().take(1).compile.toList
        _ = x.head.message shouldBe BlockBodies(blockBodies)
      } yield ()

      p.attempt.unsafeRunTimed(5.seconds)
      stopAll.unsafeRunSync()
    }

    "return BlockHeaders by block number/hash" in new SyncServiceFixture {

      val baseHeader: BlockHeader = BlockHeader.empty
      val firstHeader: BlockHeader = baseHeader.copy(number = 3)
      val secondHeader: BlockHeader = baseHeader.copy(number = 4)

      val p = for {
        _ <- connect
        _ <- syncService.start
        _ <- blockchain.save(firstHeader)
        _ <- blockchain.save(secondHeader)
        _ <- blockchain.save(baseHeader.copy(number = 5))
        _ <- blockchain.save(baseHeader.copy(number = 6))
        _ <- pm2.broadcast(GetBlockHeaders(Left(3), 2, 0, reverse = false))
        x <- pm2.subscribeMessages().take(1).compile.toList
        _ = x.head.message shouldBe BlockHeaders(firstHeader :: secondHeader :: Nil)

        _ <- pm2.broadcast(GetBlockHeaders(Right(firstHeader.hash), 2, 0, reverse = false))
        x <- pm2.subscribeMessages().take(1).compile.toList
        _ = x.head.message shouldBe BlockHeaders(firstHeader :: secondHeader :: Nil)
      } yield ()

      p.attempt.unsafeRunTimed(5.seconds)
      stopAll.unsafeRunSync()
    }
  }
}
