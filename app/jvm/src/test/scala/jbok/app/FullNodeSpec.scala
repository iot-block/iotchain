package jbok.app

import cats.effect.IO
import cats.implicits._
import jbok.JbokSpec
import jbok.common.execution._
import jbok.core.config.Configs.FullNodeConfig
import jbok.core.testkit._

import scala.concurrent.duration._

class FullNodeSpec extends JbokSpec {
  implicit val fixture = defaultFixture()

  def newFullNode(config: FullNodeConfig): FullNode[IO] = {
    implicit val fix = fixture.copy(port = config.peer.port)
    FullNode.forConfigAndConsensus(config, fix.consensus.unsafeRunSync()).unsafeRunSync()
  }

  "FullNode" should {
    "create a full node" in {
      val fullNodeConfig = FullNodeConfig("1", 10001)
      val fullNode       = newFullNode(fullNodeConfig)
      val p = for {
        _ <- fullNode.start
        _ <- T.sleep(2.second)
        _ <- fullNode.stop
      } yield ()

      p.unsafeRunSync()
    }

    "create a bunch of nodes and connect with ring" in {
      val configs = FullNodeConfig.fill(10)
      val nodes   = configs.map(config => newFullNode(config))

      println(nodes.map(_.peerManager.peerNode.uri).mkString("\n"))

      val p = for {
        _ <- nodes.traverse(_.start)
        _ <- T.sleep(3.seconds)
        _ <- (nodes :+ nodes.head).sliding(2).toList.traverse[IO, Unit] {
          case a :: b :: Nil =>
            a.peerManager.addPeerNode(b.peerNode)
          case _ =>
            IO.unit
        }
        _ <- T.sleep(3.seconds)
        _ = nodes.foreach(_.peerManager.peerSet.connected.unsafeRunSync().size shouldBe 2)
        _ <- nodes.traverse(_.stop)
      } yield ()

      p.unsafeRunSync()
    }

    "create a bunch of nodes and connect with star" in {
      val N       = 10
      val configs = FullNodeConfig.fill(N)
      val nodes   = configs.map(config => newFullNode(config))

      val p = for {
        _             <- nodes.traverse(_.start)
        _             <- T.sleep(3.seconds)
        _             <- nodes.traverse(_.peerManager.addPeerNode(nodes.head.peerNode))
        _             <- T.sleep(3.seconds)
        headConnected <- nodes.head.peerManager.peerSet.connected
        _ = headConnected.size shouldBe N - 1
        _ = nodes.tail.foreach(_.peerManager.peerSet.connected.unsafeRunSync().size shouldBe 1)
        _ <- nodes.traverse(_.stop)
      } yield ()

      p.unsafeRunSync()
    }

    "create a bunch of nodes and connect with star and broadcast some blocks" in {
      val N       = 4
      val configs = FullNodeConfig.fill(N)
      val nodes   = configs.map(config => newFullNode(config))

      val miner = nodes.head.miner
      val p = for {
        _ <- nodes.traverse(_.start)
        _ <- T.sleep(3.seconds)
        _ <- nodes.traverse(_.peerManager.addPeerNode(nodes.head.peerNode))
        _ <- T.sleep(3.seconds)
        _     = nodes.head.peerManager.peerSet.connected.unsafeRunSync().size shouldBe N - 1
        mined = miner.stream.take(1).compile.toList.unsafeRunSync().head
        _ <- T.sleep(3.seconds)
        _ = nodes.head.history.getBestBlock.unsafeRunSync() shouldBe mined
        _ <- nodes.traverse(_.stop)
      } yield ()
      p.unsafeRunSync()
    }
  }
}
