package jbok.core

import cats.effect.IO
import cats.implicits._
import fs2.Stream._
import jbok.JbokSpec
import jbok.core.configs.PeerManagerConfig
import jbok.core.genesis.Genesis
import jbok.core.messages.Hello
import jbok.core.peer.PeerManager
import jbok.network.NetAddress
import jbok.network.execution._
import scodec.bits.ByteVector
import scala.concurrent.duration._

trait PeerManageFixture extends BlockChainFixture {
  val N = 3

  val peerManagerConfigs = (1 to N).toList
    .map(i => PeerManagerConfig(NetAddress("localhost", 10000 + i)))

  val pm1 = PeerManager[IO](peerManagerConfigs(0), blockChain).unsafeRunSync()
  val pm2 = PeerManager[IO](peerManagerConfigs(1), blockChain2).unsafeRunSync()
  val pm3 = PeerManager[IO](peerManagerConfigs(2), blockChain3).unsafeRunSync()

  val peerManagers = List(pm1, pm2, pm3)

  val listen = peerManagers.traverse(_.listen())
  val connect = listen.flatMap(_ => peerManagers.tail.traverse(p => peerManagers.head.connect(p.config.bindAddr)))
  val stopAll = peerManagers.traverse(_.stop)
}

class PeerManagerSpec extends JbokSpec {
  "peer manager" should {
    "get local status" in new PeerManageFixture {
      val status = pm1.getStatus.unsafeRunSync()
      status.genesisHash shouldBe Genesis.header.hash
    }

    "handshake" in new PeerManageFixture {
      val p = for {
        _ <- connect
        peers <- peerManagers.traverse(_.getHandshakedPeers)
        _ = peers.map(_.size).sum shouldBe 4
      } yield ()

      p.attempt.unsafeRunSync()
      stopAll.unsafeRunSync()
    }

    "create connections and send messages" in new PeerManageFixture {
      val message = Hello(1, "", 0, ByteVector.empty)

      val p = for {
        _ <- connect
        e <- pm1
          .subscribeMessages()
          .concurrently(eval(pm2.broadcast(message)) ++ eval(pm3.broadcast(message)))
          .take(2)
          .compile
          .toList
        _ = e.map(_.message) shouldBe List.fill(2)(message)
      } yield ()

      p.attempt.unsafeRunTimed(5.seconds)
      stopAll.unsafeRunSync()
    }

    "retry connections to remaining bootstrap nodes" ignore {}

    "publish disconnect messages from peer" ignore {}

    "ignore established connections" ignore {}
  }
}
