package jbok.core

import java.net.InetSocketAddress

import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import jbok.core.configs.FullNodeConfig
import jbok.core.ledger.{BlockPool, Ledger, OmmersPool}
import jbok.core.peer.PeerManager
import jbok.core.sync.{Broadcaster, RegularSync, SyncService}
import jbok.core.validators.Validators
import jbok.evm.VM

import scala.concurrent.ExecutionContext

case class FullNode[F[_]](
    config: FullNodeConfig,
    peerManager: PeerManager[F],
    txPool: TxPool[F],
    regularSync: RegularSync[F],
    syncService: SyncService[F]
)(implicit F: ConcurrentEffect[F], EC: ExecutionContext, T: Timer[F]) {
  val id = config.nodeId

  val peerBindAddress: InetSocketAddress =
    new InetSocketAddress(config.network.peerBindAddress.host, config.network.peerBindAddress.port.get)

  def start: F[Unit] =
    for {
      _ <- peerManager.listen
      _ <- txPool.start
      _ <- regularSync.start
      _ <- syncService.start
    } yield ()

  def stop: F[Unit] =
    for {
      _ <- syncService.stop
      _ <- regularSync.stop
      _ <- txPool.stop
      _ <- peerManager.stop
    } yield ()
}

object FullNode {
  def inMemory[F[_]](config: FullNodeConfig)(
      implicit F: ConcurrentEffect[F],
      EC: ExecutionContext,
      T: Timer[F]
  ): F[FullNode[F]] =
    for {
      blockChain  <- BlockChain.inMemory[F]()
      peerManager <- PeerManager[F](config.peer, blockChain)
      txPoolConfig = TxPoolConfig()
      txPool     <- TxPool[F](peerManager, txPoolConfig)
      blockPool  <- BlockPool[F](blockChain, 10, 10)
      ommersPool <- OmmersPool[F](blockChain)
      broadcaster = new Broadcaster[F](peerManager)
      vm          = new VM
      validators  = Validators[F](blockChain, config.blockChainConfig, config.daoForkConfig)
      ledger      = new Ledger[F](vm, blockChain, config.blockChainConfig, validators, blockPool)
      regularSync <- RegularSync[F](peerManager, ledger, ommersPool, txPool, broadcaster)
      syncService <- SyncService[F](peerManager, blockChain)
    } yield
      FullNode[F](
        config,
        peerManager,
        txPool,
        regularSync,
        syncService
      )
}
