package jbok.core.peer

import cats.effect.IO
import cats.implicits._
import fs2._
import jbok.JbokSpec
import jbok.common.execution._
import jbok.core.HistoryFixture
import jbok.core.config.Configs.{PeerManagerConfig, SyncConfig}
import jbok.core.messages.{BlockBodies, GetBlockBodies, Handshake}
import scodec.bits.ByteVector

import scala.concurrent.duration._

class PeersFixture(n: Int = 3) {
  val pms = (1 to n).toList.map(i => new PeerManagerFixture(10000 + i))

  def startAll =
    pms.traverse(_.pm.start) *> T.sleep(1.seconds) *> pms.traverse(_.pm.addKnown(pms.head.addr)) *> T.sleep(1.seconds)

  def stopAll = pms.traverse(_.pm.stop).void
}

class PeerManagerFixture(port: Int) extends HistoryFixture {
  val pmConfig = PeerManagerConfig(port)
  val addr     = pmConfig.bindAddr
  val pm       = PeerManager[IO](pmConfig, SyncConfig(), history).unsafeRunSync()
}

class PeerManagerSpec extends JbokSpec {
  "PeerManager" should {
    "keep incoming connections <= maxOpen" in {
      val fix1 = new PeerManagerFixture(10001)
      val fix2 = new PeerManagerFixture(10002)
      val fix3 = new PeerManagerFixture(10003)
      val maxOpen = 1

      val p = for {
        fiber    <- fix1.pm.listen(maxOpen = maxOpen).compile.drain.start
        _        <- T.sleep(1.seconds)
        _        <- fix2.pm.addKnown(fix1.addr)
        _        <- fix3.pm.addKnown(fix1.addr)
        _        <- fix2.pm.connect().compile.drain.start
        _        <- fix3.pm.connect().compile.drain.start
        _        <- T.sleep(1.seconds)
        incoming <- fix1.pm.incoming.get
        _ = incoming.size shouldBe maxOpen
        _ <- fiber.cancel
      } yield ()

      p.unsafeRunSync()
    }

    "keep outgoing connections <= maxOpen" in {
      val fix1 = new PeerManagerFixture(10001)
      val fix2 = new PeerManagerFixture(10002)
      val fix3 = new PeerManagerFixture(10003)
      val maxOpen = 1

      val p = for {
        fiber    <- Stream(fix1.pm, fix2.pm, fix3.pm).map(_.listen()).parJoinUnbounded.compile.drain.start
        _        <- T.sleep(1.seconds)
        _        <- fix1.pm.addKnown(fix2.addr, fix3.addr)
        _        <- fix1.pm.connect(maxOpen).compile.drain.start
        _        <- T.sleep(1.seconds)
        outgoing <- fix1.pm.outgoing.get
        _ = outgoing.size shouldBe maxOpen
        _ <- fiber.cancel
      } yield ()

      p.unsafeRunSync()
    }

    "get local status" in {
      val fix = new PeerManagerFixture(10001)
      val status = fix.pm.localStatus.unsafeRunSync()
      status.genesisHash shouldBe fix.history.getBestBlock.unsafeRunSync().header.hash
    }

    "broadcast and subscribe" in new PeersFixture(3) {
      val message = Handshake(1, 0, ByteVector.empty)
      val p = for {
        _        <- startAll
        _        <- pms.tail.traverse(_.pm.broadcast(message, None))
        messages <- pms.head.pm.subscribe.take(2).compile.toList
        _ = messages.map(_._2) shouldBe List.fill(2)(message)
        _ <- stopAll
      } yield ()

      p.unsafeRunSync()
    }

    "request and response" in new PeersFixture(2) {
      val message = GetBlockBodies(Nil)
      val p = for {
        _    <- startAll
        resp <- pms.head.pm.incoming.get.map(_.head._2).unsafeRunSync().conn.request(message)
        _ = resp shouldBe a[BlockBodies]
        _ <- stopAll
      } yield ()

      p.unsafeRunSync()
    }

    "close connection explicitly" in new PeersFixture(2) {
      val p = for {
        _ <- startAll

        incoming <- pms.head.pm.incoming.get
        outgoing <- pms.last.pm.outgoing.get
        _ = incoming.size shouldBe 1
        _ = outgoing.size shouldBe 1

        _        <- pms.last.pm.close(pms.head.addr)
        _        <- T.sleep(1.seconds)
        incoming <- pms.head.pm.incoming.get
        outgoing <- pms.last.pm.outgoing.get
        _ = incoming.size shouldBe 0
        _ = outgoing.size shouldBe 0
        _ <- stopAll
      } yield ()

      p.unsafeRunSync()
    }
  }
}
