package jbok.core

import java.net.InetSocketAddress
import java.security.SecureRandom

import cats.effect._
import cats.implicits._
import jbok.core.Configs.FullNodeConfig
import jbok.core.consensus.pow.ethash.{EthashConsensus, EthashHeaderValidator, EthashMiner, EthashOmmersValidator}
import jbok.core.keystore.KeyStore
import jbok.core.ledger.BlockExecutor
import jbok.core.mining.BlockMiner
import jbok.core.peer.PeerManager
import jbok.core.pool.{BlockPool, OmmerPool, TxPool}
import jbok.core.sync.{Broadcaster, SyncService, Synchronizer}
import jbok.persistent.KeyValueDB

import scala.concurrent.ExecutionContext

case class FullNode[F[_]](
    config: FullNodeConfig,
    peerManager: PeerManager[F],
    synchronizer: Synchronizer[F],
    syncService: SyncService[F],
    keyStore: KeyStore[F],
    miner: Option[BlockMiner[F]]
)(implicit F: ConcurrentEffect[F]) {
  val id = config.nodeId

  val peerBindAddress: InetSocketAddress =
    new InetSocketAddress(config.peer.bindAddr.host, config.peer.bindAddr.port.get)

  def start: F[Unit] =
    for {
      _ <- peerManager.listen
      _ <- synchronizer.start
      _ <- syncService.start
    } yield ()

  def stop: F[Unit] =
    for {
      _ <- syncService.stop
      _ <- synchronizer.stop
      _ <- peerManager.stop
    } yield ()
}

object FullNode {
  def inMemory[F[_]](
      config: FullNodeConfig)(implicit F: ConcurrentEffect[F], EC: ExecutionContext, T: Timer[F]): F[FullNode[F]] = {
    val random = new SecureRandom()
    for {
      db          <- KeyValueDB.inMemory[F]
      history     <- History[F](db)
      _           <- history.loadGenesis()
      peerManager <- PeerManager[F](config.peer, history)
      blockPool   <- BlockPool(history)
      ommersValidator = new EthashOmmersValidator[F](history, config.blockChainConfig, config.daoForkConfig)
      headerValidator = new EthashHeaderValidator[F](config.blockChainConfig, config.daoForkConfig)
      realMiner <- EthashMiner[F](config.miningConfig)
      consensus = new EthashConsensus(config.blockChainConfig,
                                      config.miningConfig,
                                      history,
                                      blockPool,
                                      realMiner,
                                      ommersValidator,
                                      headerValidator)
      executor = BlockExecutor[F](config.blockChainConfig, history, blockPool, consensus)
      txPool    <- TxPool[F](peerManager)
      ommerPool <- OmmerPool[F](history)
      broadcaster = new Broadcaster[F](peerManager)
      synchronizer <- Synchronizer[F](peerManager, executor, txPool, ommerPool, broadcaster)
      syncService  <- SyncService[F](peerManager, history)
      keyStore     <- KeyStore[F](config.keystore.keystoreDir, random)
      miner        <- BlockMiner[F](synchronizer)
    } yield new FullNode[F](config, peerManager, synchronizer, syncService, keyStore, Some(miner))
  }

}
