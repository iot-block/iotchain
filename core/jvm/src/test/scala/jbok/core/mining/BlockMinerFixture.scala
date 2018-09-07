package jbok.core.mining

import cats.effect.IO
import jbok.core.Configs.{BlockChainConfig, PeerManagerConfig}
import jbok.core.consensus.ConsensusFixture
import jbok.core.ledger.BlockExecutor
import jbok.core.peer.PeerManager
import jbok.core.pool._
import jbok.core.sync.{Broadcaster, Synchronizer}
import jbok.network.NetAddress
import jbok.network.execution._

class BlockMinerFixture(consensusFixture: ConsensusFixture, bindAddr: NetAddress = NetAddress("localhost", 9999)) {
  val txGen            = consensusFixture.txGen
  val consensus        = consensusFixture.consensus
  val blockChainConfig = BlockChainConfig()
  val history          = consensus.history

  val blockPoolConfig = BlockPoolConfig()
  val blockPool       = BlockPool[IO](history, blockPoolConfig).unsafeRunSync()
  val executor        = BlockExecutor[IO](blockChainConfig, history, blockPool, consensus)

  val peerManagerConfig = PeerManagerConfig(bindAddr)
  val peerManager       = PeerManager[IO](peerManagerConfig, history).unsafeRunSync()
  val txPool            = TxPool[IO](peerManager, TxPoolConfig()).unsafeRunSync()
  val ommerPool         = OmmerPool[IO](history).unsafeRunSync()
  val broadcaster       = new Broadcaster[IO](peerManager)
  val synchronizer      = Synchronizer[IO](peerManager, executor, txPool, ommerPool, broadcaster).unsafeRunSync()

  val miner = BlockMiner[IO](synchronizer).unsafeRunSync()
}
