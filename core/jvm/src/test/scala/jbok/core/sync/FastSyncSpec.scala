package jbok.core.sync

import cats.effect.IO
import jbok.JbokSpec
import jbok.common.execution._
import jbok.core.config.Configs.SyncConfig
import jbok.core.consensus.poa.clique.CliqueFixture
import jbok.core.mining.BlockMinerFixture

import scala.concurrent.duration._

class FastSyncSpec extends JbokSpec {
  val cliqueFixture = new CliqueFixture {}
  val miner1         = new BlockMinerFixture(cliqueFixture, 10001)
  val miner2 = new BlockMinerFixture(cliqueFixture, 10002)

  val config = SyncConfig(
    minPeersToChooseTargetBlock = 1,
    targetBlockOffset = 0
  )

  val fastSync = FastSync[IO](config, miner2.peerManager).unsafeRunSync()

  val p = for {
    _ <- miner1.peerManager.start
    _ <- miner2.peerManager.start
    _ <- T.sleep(1.seconds)
    _ <- miner2.peerManager.addPeerNode(miner1.peerManager.peerNode)
    _ <- T.sleep(1.seconds)
  } yield ()

  p.unsafeRunSync()

  "FastSync" should {
    "get peer status" in {
//      fastSync.start.compile.drain.unsafeRunSync()
    }
  }

  override protected def afterAll(): Unit = {
    miner1.peerManager.stop.unsafeRunSync()
    miner2.peerManager.stop.unsafeRunSync()
  }
}
