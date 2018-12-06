package jbok.network.transport

import java.net.InetSocketAddress

import cats.effect.{ConcurrentEffect, ContextShift}
import cats.implicits._
import fs2.io.udp
import fs2.io.udp.{AsynchronousSocketGroup, Packet}
import fs2.{Pipe, _}
import jbok.network.common.TcpUtil
import scodec.Codec

case class UdpTransport[F[_]](bind: InetSocketAddress)(implicit F: ConcurrentEffect[F], CS: ContextShift[F]) {
  private[this] val log = org.log4s.getLogger("UdpTransport")

  implicit val AG = AsynchronousSocketGroup()

  def serve[A: Codec](pipe: Pipe[F, (InetSocketAddress, A), (InetSocketAddress, A)]): Stream[F, Unit] =
    Stream
      .resource(udp.Socket[F](bind, reuseAddress = true))
      .flatMap { socket =>
        log.debug(s"successfully bound to ${bind}")
        socket
          .reads()
          .evalMap(p =>
            TcpUtil
              .decodeChunk(p.bytes)
              .map(a => {
                log.trace(s"received msg from ${p.remote}")
                p.remote -> a
              }))
          .through(pipe)
          .evalMap {
            case (remote, a) =>
              TcpUtil.encode(a).map(chunk => Packet(remote, chunk))
          }
          .to(socket.writes())
      }
      .handleErrorWith(e => Stream.eval(F.delay(log.warn(e)(s"transport error"))))
      .onFinalize(F.delay(log.trace(s"serving terminated")))

  def send[A: Codec](remote: InetSocketAddress, a: A): F[Unit] = {
    log.trace(s"sending msg to ${remote}")
    udp.Socket[F](bind, reuseAddress = true).use { socket =>
      TcpUtil.encode(a).flatMap(chunk => socket.write(Packet(remote, chunk)))
    }
  }
}
