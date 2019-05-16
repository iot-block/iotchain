package jbok.core.peer

import cats.effect._
import cats.implicits._
import fs2._
import jbok.core.peer.PeerSelector.PeerSelector
import jbok.network.Message

final class PeerManager[F[_]](
    val incoming: IncomingManager[F],
    val outgoing: OutgoingManager[F],
)(implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F]) {
  val inbound: Stream[F, (Peer[F], Message[F])] =
    incoming.inbound.consume merge outgoing.inbound.consume

  val outbound: Pipe[F, (PeerSelector[F], Message[F]), Unit] =
    _.evalMap { case (selector, message) => distribute(selector, message) }

  val connected: F[List[Peer[F]]] =
    for {
      in  <- incoming.connected.get
      out <- outgoing.connected.get
    } yield (in ++ out).values.map(_._1).toList

  def distribute(selector: PeerSelector[F], message: Message[F]): F[Unit] =
    for {
      peers    <- connected
      selected <- selector.run(peers)
      _        <- selected.traverse(_.queue.enqueue1(message))
    } yield ()

  def close(uri: PeerUri): F[Unit] =
    (incoming.close(uri), outgoing.close(uri)).tupled.void

  val stream: Stream[F, Unit] =
    Stream(
      incoming.serve,
      outgoing.connects,
    ).parJoinUnbounded

  val resource: Resource[F, PeerUri] = {
    for {
      peerUri <- incoming.resource
      _       <- outgoing.resource
    } yield peerUri
  }
}
