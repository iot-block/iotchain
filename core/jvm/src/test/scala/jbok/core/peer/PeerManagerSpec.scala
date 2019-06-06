package jbok.core.peer

import cats.effect.IO
import cats.implicits._
import jbok.core.CoreSpec
import jbok.core.models.ChainId
import monocle.macros.syntax.lens._

import scala.concurrent.duration._

class PeerManagerSpec extends CoreSpec {
  "PeerManager" should {
    val List(config1, config2, config3) = List.fill(3)(config.lens(_.peer.port).set(0))

    "keep incoming connections <= maxIncomingPeers" in {
      val p = List(config1.lens(_.peer.maxIncomingPeers).set(1), config2, config3).traverse(testCoreResource).use {
        case List(obj1, obj2, obj3) =>
          val pm1 = obj1.get[PeerManager[IO]]
          val pm2 = obj2.get[PeerManager[IO]]
          val pm3 = obj3.get[PeerManager[IO]]

          List(pm1, pm2, pm3).traverse(_.resource).use {
            case List(uri1, _, _) =>
              for {
                _        <- pm2.outgoing.store.add(uri1)
                _        <- pm3.outgoing.store.add(uri1)
                _        <- timer.sleep(2.second)
                incoming <- pm1.incoming.connected.get
                _ = incoming.size shouldBe 1
              } yield ()
          }
      }
      p.unsafeRunSync()
    }

    "keep outgoing connections <= maxOutgoingPeers" in {
      val p = List(config1.lens(_.peer.maxOutgoingPeers).set(1), config2, config3).traverse(testCoreResource).use {
        case List(obj1, obj2, obj3) =>
          val pm1 = obj1.get[PeerManager[IO]]
          val pm2 = obj2.get[PeerManager[IO]]
          val pm3 = obj3.get[PeerManager[IO]]

          List(pm1, pm2, pm3).traverse(_.resource).use {
            case List(_, uri2, uri3) =>
              for {
                _        <- pm1.outgoing.store.add(uri2, uri3)
                _        <- timer.sleep(2.seconds)
                outgoing <- pm1.outgoing.connected.get
                _ = outgoing.size shouldBe 1
              } yield ()
          }
      }
      p.unsafeRunSync()
    }

    "fail if peers are incompatible" in {
      val p = List(config1, config2.lens(_.genesis.chainId).set(ChainId(2)), config3).traverse(testCoreResource).use {
        case List(obj1, obj2, obj3) =>
          val pm1 = obj1.get[PeerManager[IO]]
          val pm2 = obj2.get[PeerManager[IO]]
          val pm3 = obj3.get[PeerManager[IO]]

          List(pm1, pm2, pm3).traverse(_.resource).use {
            case List(uri1, _, _) =>
              for {
                _        <- pm2.outgoing.store.add(uri1)
                _        <- timer.sleep(2.seconds)
                incoming <- pm1.incoming.connected.get
                _ = incoming.size shouldBe 0
              } yield ()
          }
      }
      p.unsafeRunSync()
    }

    "close connection explicitly" in {
      val p = List(config1, config2, config3).traverse(testCoreResource).use {
        case List(obj1, obj2, obj3) =>
          val pm1 = obj1.get[PeerManager[IO]]
          val pm2 = obj2.get[PeerManager[IO]]
          val pm3 = obj3.get[PeerManager[IO]]

          List(pm1, pm2, pm3).traverse(_.resource).use {
            case List(uri1, _, _) =>
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
      }
      p.unsafeRunSync()
    }
  }
}
