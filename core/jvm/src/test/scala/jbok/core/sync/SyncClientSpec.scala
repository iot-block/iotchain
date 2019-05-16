package jbok.core.sync

import jbok.core.CoreSpec
import jbok.core.testkit._

class SyncClientSpec extends CoreSpec {

  val List(config1, config2, config3) = fillConfigs(3)(config)

  "FullSyncClient" should {
//    "sync to the highest state" in {
//      val locator1 = withConfig(config1).unsafeRunSync()
//      val locator2 = withConfig(config2).unsafeRunSync()
//      val locator3 = withConfig(config3).unsafeRunSync()
//
//      val history1 = locator1.get[History[IO]]
//      val history2 = locator2.get[History[IO]]
//      val history3 = locator3.get[History[IO]]
//
////      val pm1 = locator1.get[PeerManager[IO]]
////      val pm2 = locator2.get[PeerManager[IO]]
////      val pm3 = locator3.get[PeerManager[IO]]
//
//      val sm1 = locator1.get[SyncManager[IO]]
//      val sm2 = locator2.get[SyncManager[IO]]
//      val sm3 = locator3.get[SyncManager[IO]]
//      val miner = locator1.get[BlockMiner[IO]]
//
//      val fs1 = locator1.get[FullSync[IO]]
//      val fs2 = locator2.get[FullSync[IO]]
//      val fs3 = locator3.get[FullSync[IO]]
//
//      val N      = 10
//      val blocks = miner.stream.take(N).compile.toList.unsafeRunSync()
//
//      val p = for {
//        fiber <- Stream(pm1, pm2, pm3).map(_.stream).parJoinUnbounded.compile.drain.start
//        _     <- timer.sleep(1.second)
//        _     <- sm1.serve.compile.drain.start
//        _     <- timer.sleep(1.second)
//        _     <- pm2.addPeerNode(pm1.peerNode)
//        _     <- pm3.addPeerNode(pm1.peerNode)
//        _     <- timer.sleep(1.second)
//
//        _     <- fs2.stream.take(N).compile.drain
//        _     <- fs3.stream.take(N).compile.drain
//        best1 <- history1.getBestBlock
//        best2 <- history2.getBestBlock
//        best3 <- history3.getBestBlock
//        _ = best1 shouldBe blocks.last.block
//        _ = best1 shouldBe best2
//        _ = best1 shouldBe best3
//        _ <- fiber.cancel
//      } yield ()
//
//      p.unsafeRunSync()
//    }
  }
}
