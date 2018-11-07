package jbok.core.sync
import jbok.JbokSpec
import jbok.common.execution._
import jbok.core.consensus.poa.clique.CliqueFixture
import jbok.core.mining.BlockMinerFixture

class FullSyncSpec extends JbokSpec {
  "FullSync" should {
    val fix1 = new BlockMinerFixture(new CliqueFixture {}, 20000)
    val fix2 = new BlockMinerFixture(new CliqueFixture {}, 20001)
    fix1.history.init(fix1.genesisConfig).unsafeRunSync()
    fix2.history.init(fix1.genesisConfig).unsafeRunSync()

    "sync all nodes to a single highest state" in {
      fix1.peerManager.start.unsafeRunSync()

      val N = 3
      fix1.miner.miningStream.take(N).compile.toList.unsafeRunSync()

      fix2.peerManager.addPeerNode(fix1.peerManager.peerNode).unsafeRunSync()
      Thread.sleep(2000)

      // miner2 sync to miner1
      fix2.fullSync.stream.take(1).compile.drain.unsafeRunSync()
      fix1.peerManager.stop.unsafeRunSync()
    }
  }
}
