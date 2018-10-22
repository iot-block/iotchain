package jbok.network.server

import java.net.InetSocketAddress

import cats.effect.ConcurrentEffect
import cats.effect.concurrent.Ref
import fs2._
import cats.implicits._
import jbok.network.Connection
import jbok.network.common.TcpUtil
import jbok.common.execution._
import scodec.Codec

class TcpServerBuilder[F[_], A: Codec](implicit F: ConcurrentEffect[F]) extends ServerBuilder[F, A] {
  private[this] val log = org.log4s.getLogger

  override def listen(bind: InetSocketAddress,
                      pipe: Pipe[F, A, A],
                      conns: Ref[F, Map[InetSocketAddress, Connection[F, A]]],
                      maxConcurrent: Int,
                      maxQueued: Int,
                      reuseAddress: Boolean,
                      receiveBufferSize: Int): Stream[F, Unit] =
    fs2.io.tcp
      .serverWithLocalAddress[F](bind, maxQueued, reuseAddress, receiveBufferSize)
      .map {
        case Left(bindAddr) =>
          log.info(s"server bound to ${bindAddr}")
          Stream.empty.covary[F]
        case Right(s) =>
          Stream.resource(s).flatMap(socket => {
            val conn: Connection[F, A] = TcpUtil.socketToConnection[F, A](socket, true)
            for {
              remote <- Stream.eval(conn.remoteAddress)
              _      <- Stream.eval(conns.update(_ + (remote -> conn)))
              _      <- conn.reads().through(pipe).to(conn.writes()).onFinalize(conns.update(_ - remote).void)
            } yield ()
          })
      }
      .parJoin(maxConcurrent)
      .handleErrorWith(e => Stream.eval[F, Unit](F.delay(log.error(e)(s"server error: ${e}"))))
}

object TcpServerBuilder {
  def apply[F[_]: ConcurrentEffect, A: Codec] = new TcpServerBuilder[F, A]
}
