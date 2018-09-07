package jbok.core

import java.net.InetSocketAddress

import cats.effect.ConcurrentEffect
import cats.implicits._
import jbok.core.Configs.FullNodeConfig
import jbok.core.keystore.KeyStore
import jbok.core.mining.BlockMiner
import jbok.core.sync.{SyncService, Synchronizer}

case class FullNode[F[_]](
    config: FullNodeConfig,
    synchronizer: Synchronizer[F],
    syncService: SyncService[F],
    keyStore: KeyStore[F],
    miner: Option[BlockMiner[F]]
)(implicit F: ConcurrentEffect[F]) {
  val id = config.nodeId

  val peerBindAddress: InetSocketAddress =
    new InetSocketAddress(config.network.peerBindAddress.host, config.network.peerBindAddress.port.get)

  def start: F[Unit] =
    for {
      _ <- synchronizer.start
      _ <- syncService.start
    } yield ()

  def stop: F[Unit] =
    for {
      _ <- syncService.stop
      _ <- synchronizer.stop
    } yield ()
}
