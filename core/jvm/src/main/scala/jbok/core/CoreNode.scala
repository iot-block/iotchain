package jbok.core

import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import fs2._
import jbok.common.log.Logger
import jbok.core.config.FullConfig
import jbok.core.ledger.{BlockExecutor, History}
import jbok.core.mining.BlockMiner
import jbok.core.peer.{PeerManager, PeerMessageHandler}
import jbok.core.pool.TxPool
import jbok.core.sync.SyncClient
import scala.concurrent.duration._

final case class CoreNode[F[_]](
    config: FullConfig,
    nodeStatus: Ref[F, NodeStatus],
    history: History[F],
    peerManager: PeerManager[F],
    executor: BlockExecutor[F],
    miner: BlockMiner[F],
    txPool: TxPool[F],
    handler: PeerMessageHandler[F],
    syncClient: SyncClient[F]
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  private val log = Logger[F]

  val logStatus: Stream[F, Unit] =
    Stream.eval {
      for {
        number <- history.getBestBlockNumber
        td     <- history.getTotalDifficultyByNumber(number).map(_.getOrElse(BigInt(0)))
        status <- nodeStatus.get
        _      <- log.i(s"status=${status},bestNumber=${number},td=${td}")
        _      <- T.sleep(10.seconds)
      } yield ()
    }.repeat

  val stream: Stream[F, Unit] = Stream(
    peerManager.stream,
    miner.stream,
    txPool.stream,
    executor.stream,
    handler.stream,
    syncClient.stream,
    syncClient.checkSeedConnect,
    syncClient.heartBeatStream,
    syncClient.statusStream,
    logStatus
  ).parJoinUnbounded
    .handleErrorWith(e => Stream.eval(log.e("CoreNode has an unhandled failure", e)))
}
