package jbok.core.peer

import cats.effect.IO
import cats.implicits._
import jbok.core.CoreSpec
import jbok.core.ledger.History
import jbok.core.testkit._
import monocle.macros.syntax.lens._

import scala.concurrent.duration._

class PeerManagerSpec extends CoreSpec {
  "PeerManager" should {
    val List(config1, config2, config3) = fillConfigs(3)(config)

    "keep incoming connections <= maxIncomingPeers" in {
      val locator1 = withConfig(config1.lens(_.peer.maxIncomingPeers).set(1)).unsafeRunSync()
      val locator2 = withConfig(config2).unsafeRunSync()
      val locator3 = withConfig(config3).unsafeRunSync()

      val pm1 = locator1.get[PeerManager[IO]]
      val pm2 = locator2.get[PeerManager[IO]]
      val pm3 = locator3.get[PeerManager[IO]]

      val p = List(pm1, pm2, pm3).traverse(_.resource).use {
        case List(uri1, uri2, uri3) =>
          for {
            _        <- pm2.outgoing.store.add(uri1)
            _        <- pm3.outgoing.store.add(uri1)
            _        <- timer.sleep(2.second)
            incoming <- pm1.incoming.connected.get
            _ = incoming.size shouldBe 1
          } yield ()
      }
      p.unsafeRunSync()
    }

    "keep outgoing connections <= maxOutgoingPeers" in {
      val locator1 = withConfig(config1.lens(_.peer.maxOutgoingPeers).set(1)).unsafeRunSync()
      val locator2 = withConfig(config2).unsafeRunSync()
      val locator3 = withConfig(config3).unsafeRunSync()

      val pm1 = locator1.get[PeerManager[IO]]
      val pm2 = locator2.get[PeerManager[IO]]
      val pm3 = locator3.get[PeerManager[IO]]

      val p = List(pm1, pm2, pm3).traverse(_.resource).use {
        case List(uri1, uri2, uri3) =>
          for {
            _        <- pm1.outgoing.store.add(uri2, uri3)
            _        <- timer.sleep(2.seconds)
            outgoing <- pm1.outgoing.connected.get
            _ = outgoing.size shouldBe 1
          } yield ()
      }
      p.unsafeRunSync()
    }

    "get local status" in {
      val locator = withConfig(config1).unsafeRunSync()
      val history = locator.get[History[IO]]
      val pm      = locator.get[PeerManager[IO]]

      val status = pm.incoming.localStatus.unsafeRunSync()
      status.genesisHash shouldBe history.getBestBlock.unsafeRunSync().header.hash
    }

    "fail if peers are incompatible" in {
      val locator1 = withConfig(config1).unsafeRunSync()
      val locator2 = withConfig(config2.lens(_.genesis.chainId).set(2)).unsafeRunSync()

      val pm1 = locator1.get[PeerManager[IO]]
      val pm2 = locator2.get[PeerManager[IO]]

      val p = List(pm1, pm2).traverse(_.resource).use {
        case List(uri1, uri2) =>
          for {
            incoming <- pm1.incoming.connected.get
            _ = incoming.size shouldBe 0
          } yield ()
      }
      p.unsafeRunSync()
    }

    "close connection explicitly" in {
      val locator1 = withConfig(config1).unsafeRunSync()
      val locator2 = withConfig(config2).unsafeRunSync()

      val pm1 = locator1.get[PeerManager[IO]]
      val pm2 = locator2.get[PeerManager[IO]]

      val p = List(pm1, pm2).traverse(_.resource).use {
        case List(uri1, uri2) =>
          for {
            _        <- pm2.outgoing.store.add(uri1)
            _        <- timer.sleep(1.seconds)
            incoming <- pm1.incoming.connected.get
            outgoing <- pm2.outgoing.connected.get
            _ = incoming.size shouldBe 1
            _ = outgoing.size shouldBe 1
            _         <- pm2.close(uri1)
            _         <- timer.sleep(2.seconds)
            incoming2 <- pm1.incoming.connected.get
            outgoing2 <- pm2.outgoing.connected.get
            _ = outgoing2.size shouldBe 0
            _ = incoming2.size shouldBe 0
          } yield ()
      }
      p.unsafeRunSync()

    }
  }
}
