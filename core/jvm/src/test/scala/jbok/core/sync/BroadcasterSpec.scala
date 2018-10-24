package jbok.core.sync

import cats.effect.IO
import cats.implicits._
import jbok.JbokSpec
import jbok.common.execution._
import jbok.core.messages.NewBlock
import jbok.core.models.{Block, BlockBody, BlockHeader}
import jbok.core.peer.PeersFixture

import scala.concurrent.duration._

class BroadcasterFixture extends PeersFixture(3) {
  val broadcaster = Broadcaster[IO](pms.head.pm)
}

class BroadcasterSpec extends JbokSpec {
  "Broadcaster" should {
    "send a block when it is not known by the peer" in new BroadcasterFixture {
      val baseHeader: BlockHeader = BlockHeader.empty
      val header =
        baseHeader.copy(number = 1)
      val body          = BlockBody(Nil, Nil)
      val block         = Block(header, body)
      val newBlock      = NewBlock(block)

      val p = for {
        _ <- startAll
        _ <- broadcaster.broadcastBlock(newBlock)
        x <- pms.tail.traverse(_.pm.subscribe.take(1).compile.toList)
        _ = x.flatten.length shouldBe pms.length - 1
        _ <- stopAll
      } yield ()

      p.unsafeRunSync()
    }

    "not send a block only when it is known by the peer" in new BroadcasterFixture {
      val baseHeader: BlockHeader = BlockHeader.empty
      val header =
        baseHeader.copy(number = 0)
      val body          = BlockBody(Nil, Nil)
      val block         = Block(header, body)
      val newBlock      = NewBlock(block)

      val p = for {
        _ <- startAll
        _ <- broadcaster.broadcastBlock(newBlock)
        x = pms.tail.traverse(_.pm.subscribe.take(1).compile.toList.unsafeRunTimed(2.seconds))
        _ = x shouldBe None
        _ <- stopAll
      } yield ()

      p.unsafeRunSync()
    }

    "send block hashes to all peers while the blocks only to sqrt of them" ignore {}
  }
}
