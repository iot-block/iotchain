package jbok.core.sync

import cats.effect.IO
import fs2._
import jbok.JbokSpec
import jbok.common.execution._
import jbok.common.testkit._
import jbok.core.config.Configs.SyncConfig
import jbok.core.mining.BlockMiner
import jbok.core.testkit._

import scala.concurrent.duration._

class FastSyncSpec extends JbokSpec {
  "FastSync" should {
    implicit val fixture = defaultFixture()

    "download accounts, storages, and code" ignore {
      val config = SyncConfig(targetBlockOffset = 0)

      val sm1   = random[SyncManager[IO]](genSyncManager(config)(fixture))
      val sm2   = random[SyncManager[IO]](genSyncManager(config)(fixture.copy(port = fixture.port + 1)))
      val sm3   = random[SyncManager[IO]](genSyncManager(config)(fixture.copy(port = fixture.port + 2)))
      val miner = random[BlockMiner[IO]](arbBlockMiner(fixture))

      val N = 10
      // let miner mine N blocks
      val blocks = miner.miningStream().take(N).compile.toList.unsafeRunSync()
      sm1.executor.importBlocks(blocks).unsafeRunSync()
      sm2.executor.importBlocks(blocks).unsafeRunSync()

      val p = for {
        fiber <- Stream(sm1, sm2, sm3).map(_.peerManager.start).parJoinUnbounded.compile.drain.start
        _     <- T.sleep(1.second)
        _     <- Stream(sm1, sm2).map(_.runService).parJoinUnbounded.compile.drain.start
        _     <- T.sleep(1.second)
        _     <- sm3.peerManager.addPeerNode(sm1.peerManager.peerNode)
        _     <- sm3.peerManager.addPeerNode(sm2.peerManager.peerNode)
        _     <- T.sleep(1.second)

        _ <- sm3.runFastSync.compile.drain
        _ <- fiber.cancel
      } yield ()

      p.unsafeRunSync()
    }
  }
}
