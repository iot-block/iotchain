package jbok.core.sync

import cats.effect.IO
import cats.implicits._
import jbok.JbokSpec
import jbok.core.PeerManageFixture
import jbok.core.messages.NewBlock
import jbok.core.models.{Block, BlockBody, BlockHeader}

import scala.concurrent.duration._

trait BroadcasterFixture extends PeerManageFixture {
  val broadcaster = new Broadcaster[IO](pm1)
}

class BroadcasterSpec extends JbokSpec {
  "broadcast" should {
    "send a block when it is not known by the peer" in new BroadcasterFixture {
      val baseHeader: BlockHeader = BlockHeader.empty
      val header =
        baseHeader.copy(number = 1)
      val body = BlockBody(Nil, Nil)
      val block = Block(header, body)
      val newBlock = NewBlock(block)

      val p = for {
        _ <- connect
        _ <- broadcaster.broadcastBlock(newBlock)
        x <- peerManagers.tail.traverse(_.subscribeMessages().take(1).compile.toList)
        _ = x.flatten.length shouldBe peerManagers.length - 1
        _ <- stopAll
      } yield ()

      p.unsafeRunSync()
    }

    "not send a block only when it is known by the peer" in new BroadcasterFixture {
      val baseHeader: BlockHeader = BlockHeader.empty
      val header =
        baseHeader.copy(number = 0)
      val body = BlockBody(Nil, Nil)
      val block = Block(header, body)
      val newBlock = NewBlock(block)

      val p = for {
        _ <- connect
        _ <- broadcaster.broadcastBlock(newBlock)
        x = peerManagers.tail.traverse(_.subscribeMessages().take(1).compile.toList.unsafeRunTimed(1.second))
        _ = x shouldBe None
        _ <- stopAll
      } yield ()

      p.unsafeRunSync()
    }

    "send block hashes to all peers while the blocks only to sqrt of them" in new BroadcasterFixture {
      val baseHeader: BlockHeader = BlockHeader.empty
      val header =
        baseHeader.copy(number = 1)
      val body = BlockBody(Nil, Nil)
      val block = Block(header, body)
      val newBlock = NewBlock(block)

      val p = for {
        _ <- connect
        _ <- broadcaster.broadcastBlock(newBlock)
        xs = peerManagers.tail.flatMap(_.subscribeMessages().take(2).compile.toList.unsafeRunTimed(1.second))
        _ = xs.length shouldBe Math.sqrt(peerManagers.length - 1).toInt
        _ <- stopAll
      } yield ()

      p.unsafeRunSync()
    }
  }
}
