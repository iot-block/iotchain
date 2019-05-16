package jbok.core

import cats.effect.{ConcurrentEffect, Timer}
import fs2._
import jbok.common.log.Logger
import jbok.core.config.Configs.CoreConfig
import jbok.core.ledger.{BlockExecutor, History}
import jbok.core.mining.BlockMiner
import jbok.core.peer.{PeerManager, PeerMessageHandler}
import jbok.core.pool.TxPool
import jbok.core.sync.{HttpSyncService, SyncClient}

final case class CoreNode[F[_]](
    config: CoreConfig,
    history: History[F],
    peerManager: PeerManager[F],
    executor: BlockExecutor[F],
    miner: BlockMiner[F],
    txPool: TxPool[F],
    handler: PeerMessageHandler[F],
    syncService: HttpSyncService[F],
    syncClient: SyncClient[F]
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  private val log = Logger[F]

  val stream: Stream[F, Unit] = Stream(
    peerManager.stream,
    miner.stream.drain,
    txPool.stream,
    executor.stream,
    handler.stream,
    syncService.stream,
    syncClient.stream.drain
  ).parJoinUnbounded
    .handleErrorWith(e => Stream.eval(log.e("CoreNode has an unhandled failure", e)))
}
