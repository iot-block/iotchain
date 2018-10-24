package jbok.core.sync

import cats.effect.IO
import fs2._
import jbok.JbokSpec
import jbok.common.execution._
import jbok.core.HistoryFixture
import jbok.core.config.Configs.SyncConfig
import jbok.core.messages._
import jbok.core.models.{BlockBody, BlockHeader}
import scodec.bits._

class SyncServiceFixture extends HistoryFixture {
  val syncService: SyncService[IO] = SyncService[IO](SyncConfig(), history)
  val pipe                         = syncService.pipe
}

class SyncServiceSpec extends JbokSpec {
  "SyncService" should {
    "return receipts by block hashes" in new SyncServiceFixture {
      val receiptsHashes = List(
        hex"a218e2c611f21232d857e3c8cecdcdf1f65f25a4477f98f6f47e4063807f2308",
        hex"1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347"
      )

      history.save(receiptsHashes(0), Nil).unsafeRunSync()
      history.save(receiptsHashes(1), Nil).unsafeRunSync()
      val req = GetReceipts(receiptsHashes)
      val res = Stream(req).covary[IO].through(pipe).compile.toList.unsafeRunSync().head
      res shouldBe Receipts(List(Nil, Nil), req.id)
    }

    "return BlockBodies by block hashes" in new SyncServiceFixture {
      val blockBodiesHashes = List(
        hex"a218e2c611f21232d857e3c8cecdcdf1f65f25a4477f98f6f47e4063807f2308",
        hex"1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347"
      )
      val body        = BlockBody(Nil, Nil)
      val blockBodies = List(body, body)
      history.save(blockBodiesHashes(0), blockBodies(0)).unsafeRunSync()
      history.save(blockBodiesHashes(1), blockBodies(1)).unsafeRunSync()
      val req = GetBlockBodies(blockBodiesHashes)
      val res = Stream(req).covary[IO].through(pipe).compile.toList.unsafeRunSync().head
      res shouldBe BlockBodies(blockBodies, req.id)
    }

    "return BlockHeaders by block number/hash" in new SyncServiceFixture {
      val baseHeader: BlockHeader   = BlockHeader.empty
      val firstHeader: BlockHeader  = baseHeader.copy(number = 3)
      val secondHeader: BlockHeader = baseHeader.copy(number = 4)

      history.save(firstHeader).unsafeRunSync()
      history.save(secondHeader).unsafeRunSync()
      history.save(baseHeader.copy(number = 5)).unsafeRunSync()
      history.save(baseHeader.copy(number = 6)).unsafeRunSync()
      val req = GetBlockHeaders(Left(3), 2, 0, reverse = false)
      val res = Stream(req).covary[IO].through(pipe).compile.toList.unsafeRunSync().head
      res shouldBe BlockHeaders(firstHeader :: secondHeader :: Nil, req.id)
      val req2 = GetBlockHeaders(Right(firstHeader.hash), 2, 0, reverse = false)
      val res2 = Stream(req2).covary[IO].through(pipe).compile.toList.unsafeRunSync().head
      res2 shouldBe BlockHeaders(firstHeader :: secondHeader :: Nil, req2.id)
    }
  }
}
