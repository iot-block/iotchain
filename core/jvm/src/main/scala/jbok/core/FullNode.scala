package jbok.core

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup
import java.security.SecureRandom

import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import jbok.core.config.Configs.{FullNodeConfig, SyncConfig}
import jbok.core.consensus.Consensus
import jbok.core.keystore.{KeyStore, KeyStorePlatform}
import jbok.core.ledger.BlockExecutor
import jbok.core.mining.BlockMiner
import jbok.core.peer.{PeerManager, PeerManagerPlatform, PeerNode}
import jbok.core.pool.{BlockPool, OmmerPool, TxPool}
import jbok.core.sync.{Broadcaster, Synchronizer}
import jbok.crypto.signature.{ECDSA, Signature}
import jbok.network.server.Server

import scala.concurrent.ExecutionContext

case class FullNode[F[_]](
    config: FullNodeConfig,
    peerManager: PeerManager[F],
    synchronizer: Synchronizer[F],
    keyStore: KeyStore[F],
    miner: BlockMiner[F],
    rpcServer: Option[Server[F]]
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  val id = config.nodeId

  val peerBindAddress: InetSocketAddress =
    config.peer.bindAddr

  val peerNode = PeerNode.fromAddr(peerManager.keyPair.public, peerBindAddress)

  def start: F[Unit] =
    for {
      _ <- peerManager.start
      _ <- synchronizer.txPool.start
      _ <- synchronizer.start
      _ <- rpcServer.map(_.start).sequence
      _ <- if (config.miningConfig.miningEnabled) miner.start else F.pure(Unit)
    } yield ()

  def stop: F[Unit] =
    for {
      _ <- synchronizer.txPool.stop
      _ <- synchronizer.stop
      _ <- rpcServer.map(_.stop).sequence
      _ <- miner.stop
      _ <- peerManager.stop
    } yield ()
}

object FullNode {
  def apply[F[_]](config: FullNodeConfig, history: History[F], consensus: Consensus[F], blockPool: BlockPool[F])(
      implicit F: ConcurrentEffect[F],
      AG: AsynchronousChannelGroup,
      EC: ExecutionContext,
      T: Timer[F]): F[FullNode[F]] = {
    val random = new SecureRandom()
    for {
      keyPair <- F.liftIO(Signature[ECDSA].generateKeyPair())
      peerManager <- PeerManagerPlatform[F](config.peer, keyPair, SyncConfig(), history)
      executor = BlockExecutor[F](config.blockChainConfig, history, blockPool, consensus)
      txPool    <- TxPool[F](peerManager)
      ommerPool <- OmmerPool[F](history)
      broadcaster = Broadcaster[F](peerManager)
      synchronizer <- Synchronizer[F](peerManager, executor, txPool, ommerPool, broadcaster)
      keyStore     <- KeyStorePlatform[F](config.keystore.keystoreDir, random)
      miner        <- BlockMiner[F](synchronizer)
    } yield new FullNode[F](config, peerManager, synchronizer, keyStore, miner, None)
  }
}
