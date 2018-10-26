package jbok.core.sync
import jbok.JbokSpec
import jbok.core.consensus.ConsensusFixture
import jbok.core.consensus.poa.clique.CliqueFixture
import jbok.core.mining.BlockMinerFixture
import jbok.common.execution._

class FullSyncSpec extends JbokSpec {
  def check(newConsensus: () => ConsensusFixture): Unit =
    "FullSync" should {
      "sync all nodes to a single highest state" in {
        val fixture1 = newConsensus()
        val miner1 = new BlockMinerFixture(fixture1, 20000)
        miner1.peerManager.start.unsafeRunSync()

        val fixture2 = newConsensus()
        val miner2 = new BlockMinerFixture(fixture2, 20001)
        miner2.history.loadGenesisConfig(fixture1.genesisConfig).unsafeRunSync()

        val N = 3
        miner1.miner.miningStream.take(N).compile.toList.unsafeRunSync()
        miner1.history.getBestBlockNumber.unsafeRunSync() shouldBe N

        miner2.peerManager.addPeerNode(miner1.peerManager.peerNode).unsafeRunSync()
        Thread.sleep(2000)

        // miner2 sync to miner1
        miner2.fullSync.stream.take(1).compile.drain.unsafeRunSync()
        miner1.peerManager.stop.unsafeRunSync()
      }
    }

  check(() => new CliqueFixture {})
}
