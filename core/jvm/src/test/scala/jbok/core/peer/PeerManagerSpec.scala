package jbok.core.peer

import cats.effect.{Fiber, IO}
import cats.implicits._
import fs2._
import jbok.JbokSpec
import jbok.common.execution._
import jbok.common.testkit._
import jbok.core.config.Configs.FullNodeConfig
import jbok.core.testkit._

import scala.concurrent.duration._

class PeerManagerSpec extends JbokSpec {
  def startAll(pms: PeerManager[IO]*): IO[Fiber[IO, Unit]] =
    for {
      fiber <- Stream.emits(pms).map(_.stream).parJoinUnbounded.compile.drain.start
      _     <- T.sleep(1.second)
      _     <- pms.tail.toList.traverse(_.addPeerNode(pms.head.peerNode))
      _     <- T.sleep(1.second)
    } yield fiber

  "PeerManager" should {
    val List(config1, config2, config3) = fillFullNodeConfigs(3)

    "keep incoming connections <= maxOpen" in {
      val pm1 =
        random[PeerManager[IO]](genPeerManager(config1.withPeer(_.copy(maxIncomingPeers = 1))))
      val pm2 = random[PeerManager[IO]](genPeerManager(config2))
      val pm3 = random[PeerManager[IO]](genPeerManager(config3))

      val p = for {
        fiber    <- startAll(pm1, pm2, pm3)
        incoming <- pm1.incoming.get
        _ = incoming.size shouldBe 1
        _ <- fiber.cancel
      } yield ()
      p.unsafeRunSync()
    }

    "keep outgoing connections <= maxOpen" in {
      val pm1     = random[PeerManager[IO]](genPeerManager(config1))
      val pm2     = random[PeerManager[IO]](genPeerManager(config2))
      val pm3     = random[PeerManager[IO]](genPeerManager(config3))
      val maxOpen = 1

      val p = for {
        fiber    <- Stream(pm1, pm2, pm3).map(_.listen()).parJoinUnbounded.compile.drain.start
        _        <- T.sleep(1.seconds)
        _        <- pm1.addPeerNode(pm2.peerNode, pm3.peerNode)
        _        <- pm1.connect(maxOpen).compile.drain.start
        _        <- T.sleep(1.seconds)
        outgoing <- pm1.outgoing.get
        _ = outgoing.size shouldBe maxOpen
        _ <- fiber.cancel
      } yield ()

      p.unsafeRunSync()
    }

    "get local status" in {
      val pm     = random[PeerManager[IO]](genPeerManager(config1))
      val status = pm.localStatus.unsafeRunSync()
      status.genesisHash shouldBe pm.history.getBestBlock.unsafeRunSync().header.hash
    }

    "fail if peers are incompatible" in {
      val pm1 = random[PeerManager[IO]](genPeerManager(config1))
      val pm2 = random[PeerManager[IO]](genPeerManager(config2.copy(genesisConfig = Some(config2.genesis.copy(chainId = 2)))))

      val p = for {
        fiber    <- startAll(pm1, pm2)
        incoming <- pm1.incoming.get
        _ = incoming.size shouldBe 0
        _ <- fiber.cancel
      } yield ()

      p.unsafeRunSync()
    }

    "close connection explicitly" in {
      val pm1 = random[PeerManager[IO]](genPeerManager(config1))
      val pm2 = random[PeerManager[IO]](genPeerManager(config2))

      val p = for {
        fiber    <- startAll(pm1, pm2)
        incoming <- pm1.incoming.get
        outgoing <- pm2.outgoing.get
        _ = incoming.size shouldBe 1
        _ = outgoing.size shouldBe 1
        _        <- pm2.close(pm1.keyPair.public)
        _        <- T.sleep(1.seconds)
        incoming <- pm1.incoming.get
        outgoing <- pm2.outgoing.get
        _ = incoming.size shouldBe 0
        _ = outgoing.size shouldBe 0
        _ <- fiber.cancel
      } yield ()

      p.unsafeRunSync()
    }
  }
}
