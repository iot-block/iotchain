package jbok.core.mining

import cats.effect.IO
import jbok.core.NodeStatus
import jbok.core.config.Configs.{BlockChainConfig, PeerManagerConfig, SyncConfig}
import jbok.core.consensus.ConsensusFixture
import jbok.core.ledger.BlockExecutor
import jbok.core.peer.PeerManager
import jbok.core.pool._
import jbok.core.sync.{Broadcaster, FullSync, Synchronizer}
import jbok.network.NetAddress
import jbok.network.execution._

class BlockMinerFixture(consensusFixture: ConsensusFixture, bindAddr: NetAddress = NetAddress("localhost", 9999)) {
  val txGen            = consensusFixture.txGen
  val consensus        = consensusFixture.consensus
  val blockChainConfig = BlockChainConfig()
  val history          = consensus.history
  val nodeStatus       = NodeStatus[IO].unsafeRunSync()
  val blockPoolConfig = BlockPoolConfig()
  val blockPool       = BlockPool[IO](history, blockPoolConfig).unsafeRunSync()
  val executor        = BlockExecutor[IO](blockChainConfig, history, blockPool, consensus)

  val syncConfig        = SyncConfig()
  val peerManagerConfig = PeerManagerConfig(bindAddr)
  val peerManager =
    PeerManager[IO](peerManagerConfig, syncConfig, nodeStatus, history).unsafeRunSync()
  val txPool       = TxPool[IO](peerManager, TxPoolConfig()).unsafeRunSync()
  val ommerPool    = OmmerPool[IO](history).unsafeRunSync()
  val broadcaster  = Broadcaster[IO](peerManager)
  val synchronizer = Synchronizer[IO](peerManager, executor, txPool, ommerPool, broadcaster).unsafeRunSync()

  val fullSync = FullSync[IO](syncConfig, peerManager, executor, txPool)
  val miner    = BlockMiner[IO](synchronizer).unsafeRunSync()
}
