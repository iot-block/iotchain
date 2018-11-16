package jbok.core.sync

import cats.SemigroupK
import cats.data.{Kleisli, OptionT}
import cats.effect.ConcurrentEffect
import cats.implicits._
import fs2._
import jbok.core.messages.Message
import jbok.core.peer._
import jbok.core.pool.TxPool

final case class SyncManager[F[_]](
    peerManager: PeerManager[F],
    requestHandler: RequestHandler[F],
    blockHandler: BlockHandler[F],
    txPool: TxPool[F],
    fastSync: FastSync[F],
    fullSync: FullSync[F]
)(implicit F: ConcurrentEffect[F]) {
  private[this] val log = org.log4s.getLogger("SyncManager")

  val executor = blockHandler.executor

  // (requestHandler.service <+> blockHandler.service).orNil
  // make intellij happy
  val service: PeerService[F] =
    SemigroupK[Kleisli[OptionT[F, ?], Request[F], ?]]
      .combineK(requestHandler.service, blockHandler.service)
      .orNil

  def run(stream: Stream[F, Request[F]]): Stream[F, Unit] =
    stream.evalMap { req =>
      log.debug(s"received request ${req.message}")
      service.run(req).flatMap { response =>
        val s: List[(Peer[F], Message)] = response
        s.traverse[F, Unit] { case (peer, message) => peer.conn.write(message) }.void
      }
    }

  def runService: Stream[F, Unit] =
    run(peerManager.messageQueue.dequeue)

  def runFullSync: Stream[F, Option[Unit]] =
    fullSync.run

  def runFastSync: Stream[F, Unit] =
    fastSync.run
}
