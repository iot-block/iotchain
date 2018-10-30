package jbok.core.peer

import cats.effect.IO
import cats.implicits._
import fs2._
import jbok.JbokSpec
import jbok.common.execution._
import jbok.core.{History, HistoryFixture}
import jbok.core.config.Configs.{PeerManagerConfig, SyncConfig}
import jbok.core.messages.{BlockBodies, GetBlockBodies, Handshake, Message}
import jbok.crypto.signature.{ECDSA, Signature}
import jbok.persistent.KeyValueDB
import scodec.bits.ByteVector

import scala.concurrent.duration._

class PeersFixture(n: Int = 3) {
  val pms = (1 to n).toList.map(i => new PeerManagerFixture(10000 + i))

  def startAll =
    pms.traverse(_.pm.start) *> T.sleep(2.seconds) *> pms.traverse(_.pm.addPeerNode(pms.head.node)) *> T.sleep(
      2.seconds)

  def stopAll = pms.traverse(_.pm.stop).void
}

class PeerManagerFixture(port: Int) extends HistoryFixture {
  val pmConfig            = PeerManagerConfig(port)
  val addr                = pmConfig.bindAddr
  val keyPair             = Signature[ECDSA].generateKeyPair().unsafeRunSync()
  val pm: PeerManager[IO] = PeerManagerPlatform[IO](pmConfig, Some(keyPair), SyncConfig(), history).unsafeRunSync()
  val node                = pm.peerNode
}

class PeerManagerSpec extends JbokSpec {
  "PeerManager" should {
    "keep incoming connections <= maxOpen" in {
      val fix1    = new PeerManagerFixture(10001)
      val fix2    = new PeerManagerFixture(10002)
      val fix3    = new PeerManagerFixture(10003)
      val maxOpen = 1

      val p = for {
        fiber    <- fix1.pm.listen(maxOpen = maxOpen).compile.drain.start
        _        <- T.sleep(2.seconds)
        _        <- fix2.pm.addPeerNode(fix1.node)
        _        <- fix3.pm.addPeerNode(fix1.node)
        _        <- fix2.pm.connect().compile.drain.start
        _        <- fix3.pm.connect().compile.drain.start
        _        <- T.sleep(2.seconds)
        incoming <- fix1.pm.incoming.get
        _ = incoming.size shouldBe maxOpen
        _ <- fiber.cancel
      } yield ()

      p.unsafeRunSync()
    }

    "keep outgoing connections <= maxOpen" in {
      val fix1    = new PeerManagerFixture(10001)
      val fix2    = new PeerManagerFixture(10002)
      val fix3    = new PeerManagerFixture(10003)
      val maxOpen = 1

      val p = for {
        fiber    <- Stream(fix1.pm, fix2.pm, fix3.pm).map(_.listen()).parJoinUnbounded.compile.drain.start
        _        <- T.sleep(2.seconds)
        _        <- fix1.pm.addPeerNode(fix2.node, fix3.node)
        _        <- fix1.pm.connect(maxOpen).compile.drain.start
        _        <- T.sleep(2.seconds)
        outgoing <- fix1.pm.outgoing.get
        _ = outgoing.size shouldBe maxOpen
        _ <- fiber.cancel
      } yield ()

      p.unsafeRunSync()
    }

    "get local status" in {
      val fix    = new PeerManagerFixture(10001)
      val status = fix.pm.localStatus.unsafeRunSync()
      status.genesisHash shouldBe fix.history.getBestBlock.unsafeRunSync().header.hash
    }

    "handshake should fail if peers are incompatible" in {
      val fix1     = new PeerManagerFixture(10001)
      val pmConfig = PeerManagerConfig(10002)
      val db       = KeyValueDB.inMemory[IO].unsafeRunSync()
      val history  = History[IO](db, fix1.history.chainId + 1).unsafeRunSync()
      history.loadGenesisConfig().unsafeRunSync()
      val keyPair = Signature[ECDSA].generateKeyPair().unsafeRunSync()
      val pm2     = PeerManagerPlatform[IO](pmConfig, Some(keyPair), SyncConfig(), history).unsafeRunSync()

      val p = for {
        fiber <- fix1.pm.listen().compile.drain.attempt.start
        _     <- T.sleep(2.seconds)
        _     <- pm2.addPeerNode(fix1.node)
        r     <- pm2.connect().compile.drain.attempt
        _     <- fiber.cancel
      } yield r

      p.unsafeRunSync().isLeft shouldBe true
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
        resp <- pms.head.pm.incoming.get.map(_.head._2).unsafeRunSync().conn.request[Message](message)
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
        _        <- T.sleep(2.seconds)
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
