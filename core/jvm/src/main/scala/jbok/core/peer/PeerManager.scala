package jbok.core.peer

import cats.effect._
import cats.implicits._
import fs2._
import jbok.common.log.Logger
import jbok.core.messages.Status
import jbok.core.models.Block
import jbok.core.peer.PeerSelector.PeerSelector
import jbok.core.queue.Producer
import jbok.network.Message

final class PeerManager[F[_]](
    val incoming: IncomingManager[F],
    val outgoing: OutgoingManager[F],
)(implicit F: ConcurrentEffect[F]) {
  private[this] val log = Logger[F]

  val inbound: Stream[F, (Peer[F], Message[F])] =
    incoming.inbound.consume
      .merge(outgoing.inbound.consume)
      .evalTap { case (peer, message) => log.i(s"received ${message} from ${peer.uri}") }

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
      _        <- log.i(s"broadcasting ${message} to ${selected.map(_.uri).mkString(",")} peers")
      _        <- selected.traverse(_.queue.enqueue1(message))
    } yield ()

  def close(uri: PeerUri): F[Unit] =
    (incoming.close(uri), outgoing.close(uri)).tupled.void

  val stream: Stream[F, Unit] =
    Stream.eval_(log.i(s"starting Core/PeerManager")) ++
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
