package jbok.core

import java.net.InetSocketAddress
import java.security.SecureRandom

import cats.effect._
import cats.implicits._
import fs2.Scheduler
import jbok.core.config.Configs.{FullNodeConfig, SyncConfig}
import jbok.core.consensus.Consensus
import jbok.core.keystore.{KeyStore, KeyStorePlatform}
import jbok.core.ledger.BlockExecutor
import jbok.core.mining.BlockMiner
import jbok.core.peer.PeerManager
import jbok.core.pool.{BlockPool, OmmerPool, TxPool}
import jbok.core.sync.{Broadcaster, Synchronizer}
import jbok.network.server.Server

import scala.concurrent.ExecutionContext

case class FullNode[F[_]](
    config: FullNodeConfig,
    peerManager: PeerManager[F],
    synchronizer: Synchronizer[F],
    keyStore: KeyStore[F],
    miner: BlockMiner[F],
    publicRpcServer: Option[Server[F, String]],
    privateRpcServer: Option[Server[F, String]]
)(implicit F: ConcurrentEffect[F]) {
  val id = config.nodeId

  val peerBindAddress: InetSocketAddress =
    new InetSocketAddress(config.peer.bindAddr.host, config.peer.bindAddr.port.get)

  def start: F[Unit] =
    for {
      _ <- peerManager.listen
      _ <- synchronizer.txPool.start
      _ <- synchronizer.start
      _ <- publicRpcServer.map(_.start).sequence
      _ <- privateRpcServer.map(_.start).sequence
      _ <- if (config.miningConfig.miningEnabled) miner.start else F.pure(Unit)
    } yield ()

  def stop: F[Unit] =
    for {
      _ <- synchronizer.txPool.stop
      _ <- synchronizer.stop
      _ <- publicRpcServer.map(_.stop).sequence
      _ <- privateRpcServer.map(_.stop).sequence
      _ <- miner.stop
      _ <- peerManager.stop
    } yield ()
}

object FullNode {
  def apply[F[_]](config: FullNodeConfig, history: History[F], consensus: Consensus[F], blockPool: BlockPool[F])(
      implicit F: ConcurrentEffect[F],
      S: Scheduler,
      EC: ExecutionContext,
      T: Timer[F]): F[FullNode[F]] = {
    val random = new SecureRandom()
    for {
      nodeStatus       <- NodeStatus[F]
      peerManager      <- PeerManager[F](config.peer, SyncConfig(), nodeStatus, history)
      executor = BlockExecutor[F](config.blockChainConfig, history, blockPool, consensus)
      txPool    <- TxPool[F](peerManager)
      ommerPool <- OmmerPool[F](history)
      broadcaster = Broadcaster[F](peerManager)
      synchronizer <- Synchronizer[F](peerManager, executor, txPool, ommerPool, broadcaster)
      keyStore     <- KeyStorePlatform[F](config.keystore.keystoreDir, random)
      miner        <- BlockMiner[F](synchronizer)
    } yield new FullNode[F](config, peerManager, synchronizer, keyStore, miner, None, None)
  }
}
