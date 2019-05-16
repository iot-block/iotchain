package jbok.app

import cats.effect.IO
import cats.implicits._
import fs2._
import jbok.app.config.{ServiceConfig}
import jbok.core.testkit._

import scala.concurrent.duration._

class FullNodeSpec extends AppSpec {
//  val coreTestconfig = testConfig
//  val config = PeerNodeConfig("", coreTestconfig, LogConfig(), ServiceConfig())

  "FullNode" should {
    "create a bunch of nodes and connect with ring" in {
      val configs = fillConfigs(10)(config)
      val locators = configs.map(config => AppModule.withConfig[IO](config).allocated.unsafeRunSync()._1)
      val nodes = locators.map(_.get[FullNode[IO]])

//      val p = for {
//        fiber <- Stream.emits(nodes).map(_.stream).parJoinUnbounded.compile.drain.start
//        _     <- timer.sleep(3.seconds)
//        _ <- (nodes :+ nodes.head).sliding(2).toList.traverse[IO, Unit] {
//          case a :: b :: Nil =>
//            a.peerManager.addPeerNode(b.peerNode)
//          case _ =>
//            IO.unit
//        }
//        _ <- T.sleep(3.seconds)
//        _ = nodes.foreach(_.peerManager.connected.unsafeRunSync().size shouldBe 2)
//        _ <- fiber.cancel
//      } yield ()
//
//      p.unsafeRunSync()
    }

    "create a bunch of nodes and connect with star" in {
      val N       = 10
      val configs = fillConfigs(N)(config)
//      val nodes   = configs.map(config => FullNode.forConfig(config).unsafeRunSync())
//
//      val p = for {
//        fiber         <- Stream.emits(nodes).map(_.stream).parJoinUnbounded.compile.drain.start
//        _             <- T.sleep(3.seconds)
//        _             <- nodes.traverse(_.peerManager.addPeerNode(nodes.head.peerNode))
//        _             <- T.sleep(3.seconds)
//        headConnected <- nodes.head.peerManager.connected
//        _ = headConnected.size shouldBe N - 1
//        _ = nodes.tail.foreach(_.peerManager.connected.unsafeRunSync().size shouldBe 1)
//        _ <- fiber.cancel
//      } yield ()
//
//      p.unsafeRunSync()
    }

    "create a bunch of nodes and connect with star and broadcast some blocks" in {
      val N       = 4
//      val configs = fillFullNodeConfigs(N)
//      val nodes   = configs.map(config => FullNode.forConfig(config).unsafeRunSync())
//
//      val miner = nodes.head.miner
//      val p = for {
//        fiber <- Stream.emits(nodes).map(_.stream).parJoinUnbounded.compile.drain.start
//        _     <- T.sleep(3.seconds)
//        _     <- nodes.traverse(_.peerManager.addPeerNode(nodes.head.peerNode))
//        _     <- T.sleep(3.seconds)
//        _     = nodes.head.peerManager.connected.unsafeRunSync().size shouldBe N - 1
//        mined = miner.stream.take(1).compile.toList.unsafeRunSync().head
//        _ <- T.sleep(3.seconds)
//        _ = nodes.head.history.getBestBlock.unsafeRunSync() shouldBe mined.block
//        _ <- fiber.cancel
//      } yield ()
//      p.unsafeRunSync()
    }
  }
}
