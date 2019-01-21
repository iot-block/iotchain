package jbok.network.transport

import java.net.InetSocketAddress

import cats.effect.{ConcurrentEffect, ContextShift, Resource}
import cats.implicits._
import fs2.io.udp
import fs2.io.udp.{AsynchronousSocketGroup, Packet, Socket}
import fs2.{Pipe, _}
import jbok.network.Message

final class UdpTransport[F[_]](socket: Socket[F])(implicit F: ConcurrentEffect[F],
                                                  CS: ContextShift[F],
                                                  AG: AsynchronousSocketGroup) {
  private[this] val log = jbok.common.log.getLogger("UdpTransport")

  def serve(pipe: Pipe[F, (InetSocketAddress, Message[F]), (InetSocketAddress, Message[F])]): Stream[F, Unit] =
    Stream
      .eval(socket.localAddress)
      .flatMap { bind =>
        log.debug(s"successfully bound to ${bind}")
        socket
          .reads()
          .evalMap(p =>
            Message
              .decodeChunk(p.bytes)
              .map(a => {
                log.trace(s"received msg from ${p.remote}")
                p.remote -> a
              }))
          .through(pipe)
          .evalMap {
            case (remote, a) =>
              a.asChunk.map(chunk => Packet(remote, chunk))
          }
          .to(socket.writes())
      }
      .handleErrorWith(e => Stream.eval(F.delay(log.warn(s"transport error", e))))
      .onFinalize(F.delay(log.trace(s"serving terminated")))

  def send(remote: InetSocketAddress, message: Message[F]): F[Unit] =
    message.asChunk.flatMap(chunk => socket.write(Packet(remote, chunk)))
}

object UdpTransport {
  def apply[F[_]](bind: InetSocketAddress)(implicit F: ConcurrentEffect[F],
                                           CS: ContextShift[F]): Resource[F, UdpTransport[F]] = {
    implicit val AG: AsynchronousSocketGroup = AsynchronousSocketGroup()
    udp.Socket[F](bind, reuseAddress = true).map(socket => new UdpTransport[F](socket))
  }
}
