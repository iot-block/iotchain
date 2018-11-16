package jbok.core.sync

import cats.effect.IO
import fs2._
import jbok.JbokSpec
import jbok.common.execution._
import jbok.common.testkit._
import jbok.core.mining.BlockMiner
import jbok.core.testkit._

import scala.concurrent.duration._

class FullSyncSpec extends JbokSpec {
  "FullSync" should {
    implicit val fixture = defaultFixture()

    "sync all nodes to a single highest state" in {
      val sm1   = random[SyncManager[IO]](arbSyncManager(fixture))
      val sm2   = random[SyncManager[IO]](arbSyncManager(fixture.copy(port = fixture.port + 1)))
      val sm3   = random[SyncManager[IO]](arbSyncManager(fixture.copy(port = fixture.port + 2)))
      val miner = random[BlockMiner[IO]](arbBlockMiner(fixture))

      // let miner mine 10 blocks first
      val blocks = miner.miningStream().take(10).compile.toList.unsafeRunSync()
      sm1.executor.importBlocks(blocks).unsafeRunSync()
      println(sm1.executor.history.getBestBlockNumber.unsafeRunSync())

      val p = for {
        fiber <- Stream(sm1, sm2, sm3).map(_.peerManager.start).parJoinUnbounded.compile.drain.start
        _     <- T.sleep(1.second)
        _     <- sm1.runService.compile.drain.start
        _     <- T.sleep(1.second)
        _     <- sm2.peerManager.addPeerNode(sm1.peerManager.peerNode)
        _     <- sm3.peerManager.addPeerNode(sm1.peerManager.peerNode)
        _     <- T.sleep(1.second)

        _ <- sm2.runFullSync.unNone.take(1).compile.drain
        _ <- fiber.cancel
      } yield ()

      p.unsafeRunSync()
    }
  }
}
