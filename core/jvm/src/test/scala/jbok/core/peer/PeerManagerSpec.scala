package jbok.core.peer

import cats.effect.IO
import cats.implicits._
import jbok.JbokSpec
import jbok.core.HistoryFixture
import jbok.core.Configs.PeerManagerConfig
import jbok.core.genesis.GenesisConfig
import jbok.core.messages.Hello
import jbok.network.NetAddress
import jbok.network.execution._
import scodec.bits.ByteVector

trait PeerManageFixture extends HistoryFixture {
  val N = 3

  val peerManagerConfigs = (1 to N).toList
    .map(i => PeerManagerConfig(NetAddress("localhost", 10000 + i)))

  val pm1 = PeerManager[IO](peerManagerConfigs(0), history).unsafeRunSync()
  val pm2 = PeerManager[IO](peerManagerConfigs(1), history2).unsafeRunSync()
  val pm3 = PeerManager[IO](peerManagerConfigs(2), history3).unsafeRunSync()

  val peerManagers = List(pm1, pm2, pm3)

  val listen = peerManagers.traverse(_.listen)
  val connect =
    listen.flatMap(_ => peerManagers.tail.traverse(p => peerManagers.head.connect(p.localAddress.unsafeRunSync()))) *> IO (Thread.sleep(1000))
  val stopAll = peerManagers.traverse(_.stop)
}

class PeerManagerSpec extends JbokSpec with PeerManageFixture {
  "peer manager" should {
    "get local status" in {
      val status = pm1.status.unsafeRunSync()
      status.genesisHash shouldBe history.getBestBlock.unsafeRunSync().header.hash
    }

    "handshake" in {
      val p = for {
        peers <- peerManagers.traverse(_.handshakedPeers)
        _ = peers.map(_.size).sum shouldBe 4
      } yield ()

      p.unsafeRunSync()
    }

    "create connections and send messages" in {
      val message = Hello(1, "", 0, ByteVector.empty)
      val p = for {
        _ <- pm2.broadcast(message)
        _ <- pm3.broadcast(message)
        e <- pm1.subscribeMessages().take(2).compile.toList
        _ = e.map(_.message) shouldBe List.fill(2)(message)
      } yield ()

      p.unsafeRunSync()
    }

    "retry connections to remaining bootstrap nodes" ignore {}

    "publish disconnect messages from peer" ignore {}

    "ignore established connections" ignore {}
  }

  override protected def beforeAll(): Unit = {
    listen.unsafeRunSync()
    Thread.sleep(2000)
    connect.unsafeRunSync()
    Thread.sleep(2000)
  }

  override protected def afterAll(): Unit = {
    Thread.sleep(2000)
    stopAll.unsafeRunSync()
  }
}
