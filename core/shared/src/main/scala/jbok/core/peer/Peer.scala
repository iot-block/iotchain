package jbok.core.peer

import java.net.SocketAddress

import cats.effect.Effect
import cats.implicits._
import fs2._
import jbok.core.messages.{Message, Status}

import scala.concurrent.ExecutionContext

case class Peer[F[_]](remote: SocketAddress, incoming: Boolean, getStatus: F[Status], conn: Connection[F])(
    implicit F: Effect[F],
    EC: ExecutionContext
) {
  private[this] val log = org.log4s.getLogger

  val id: PeerId = PeerId(remote.toString)

  def handshake: F[HandshakedPeer[F]] = {
    val recv = conn.inbound
      .subscribe(1)
      .unNone
      .collectFirst {
        case status: Status =>
          log.info(s"received remote ${status} from ${remote}")
          status
      }
      .map(status => HandshakedPeer[F](remote, conn, incoming, PeerInfo(status, 0)))
      .take(1)

    val send = Stream
      .eval(getStatus.flatMap(s => conn.outbound.enqueue1(s)))

    recv.concurrently(send).compile.toList.map(_.head)
  }

  def sendStatus: F[Unit] =
    for {
      status <- getStatus
      _ <- conn.outbound.enqueue1(status)
    } yield ()
}

case class HandshakedPeer[F[_]: Effect](
    remote: SocketAddress,
    conn: Connection[F],
    incoming: Boolean,
    peerInfo: PeerInfo
)(
    implicit EC: ExecutionContext
) {
  val id: PeerId = PeerId(remote.toString)

  def send(msg: Message): F[Unit] = conn.outbound.enqueue1(msg)
}
