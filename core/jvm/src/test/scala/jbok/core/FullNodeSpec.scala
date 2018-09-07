//package jbok.core
//
//import cats.effect.IO
//import cats.implicits._
//import jbok.JbokSpec
//import jbok.core.Configs.FullNodeConfig
//import jbok.network.execution._
//
//class FullNodeSpec extends JbokSpec {
//  "FullNode" should {
//    "create a full node" in {
//      val fullNodeConfig = FullNodeConfig("1", 10001)
//      val fullNode       = FullNode.inMemory[IO](fullNodeConfig).unsafeRunSync()
//      fullNode.start.unsafeRunSync()
//      fullNode.stop.unsafeRunSync()
//    }
//
//    "create a bunch of nodes and connect with ring" in {
//      val configs = FullNodeConfig.fill(10)
//      val nodes   = configs.map(config => FullNode.inMemory[IO](config).unsafeRunSync())
//
//      nodes.traverse(_.start).unsafeRunSync()
//      (nodes :+ nodes.head).sliding(2).foreach {
//        case a :: b :: Nil =>
//          a.peerManager.connect(b.peerBindAddress).unsafeRunSync()
//        case _ =>
//          ()
//      }
//
//      Thread.sleep(1000)
//      nodes.foreach(_.peerManager.handshakedPeers.unsafeRunSync().size shouldBe 2)
//      nodes.traverse(_.stop).unsafeRunSync()
//    }
//
//    "create a bunch of nodes and connect with star" in {
//      val N = 10
//      val configs = FullNodeConfig.fill(N)
//      val nodes   = configs.map(config => FullNode.inMemory[IO](config).unsafeRunSync())
//
//      nodes.traverse(_.start).unsafeRunSync()
//      nodes.tail.foreach { node =>
//        node.peerManager.connect(nodes.head.peerBindAddress).unsafeRunSync()
//      }
//      Thread.sleep(1000)
//      nodes.head.peerManager.handshakedPeers.unsafeRunSync().size shouldBe N - 1
//      nodes.tail.foreach(_.peerManager.handshakedPeers.unsafeRunSync().size shouldBe 1)
//      nodes.traverse(_.stop).unsafeRunSync()
//    }
//  }
//}
