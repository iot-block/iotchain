package jbok.core.mining

import cats.effect.IO
import jbok.common.execution._
import jbok.core.config.Configs.{BlockChainConfig, PeerManagerConfig, SyncConfig}
import jbok.core.consensus.ConsensusFixture
import jbok.core.ledger.BlockExecutor
import jbok.core.peer.PeerManagerPlatform
import jbok.core.pool._
import jbok.core.sync.{Broadcaster, FullSync, Synchronizer}
import jbok.crypto.signature.{ECDSA, Signature}

class BlockMinerFixture(consensusFixture: ConsensusFixture, port: Int = 9999) {
  val txGen            = consensusFixture.txGen
  val consensus        = consensusFixture.consensus
  val blockChainConfig = BlockChainConfig()
  val history          = consensus.history
  val blockPool       = consensus.blockPool
  val executor        = BlockExecutor[IO](blockChainConfig, consensus)

  val syncConfig        = SyncConfig()
  val peerManagerConfig = PeerManagerConfig(port)
  val keyPair = Signature[ECDSA].generateKeyPair().unsafeRunSync()
  val peerManager =
    PeerManagerPlatform[IO](peerManagerConfig, Some(keyPair), syncConfig, history).unsafeRunSync()
  val txPool       = TxPool[IO](peerManager, TxPoolConfig()).unsafeRunSync()
  val ommerPool    = OmmerPool[IO](history).unsafeRunSync()
  val broadcaster  = Broadcaster[IO](peerManager)
  val synchronizer = Synchronizer[IO](peerManager, executor, txPool, ommerPool, broadcaster).unsafeRunSync()

  val fullSync = FullSync[IO](syncConfig, peerManager, executor, txPool)
  val miner    = BlockMiner[IO](synchronizer).unsafeRunSync()
}
