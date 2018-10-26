package jbok.network.server

import java.net.InetSocketAddress

import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import fs2._
import jbok.common.execution._
import jbok.network.Connection
import jbok.network.common.{RequestId, TcpUtil}
import scodec.Codec

class TcpServerBuilder[F[_], A: Codec: RequestId](implicit F: ConcurrentEffect[F], T: Timer[F])
    extends ServerBuilder[F, A] {
  private[this] val log = org.log4s.getLogger

  override def listen(bind: InetSocketAddress,
                      pipe: Pipe[F, A, A],
                      conns: Ref[F, Map[InetSocketAddress, Connection[F]]],
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
          Stream
            .resource(s)
            .flatMap(socket => {
              for {
                conn <- Stream.eval(TcpUtil.socketToConnection[F](socket, true))
                _    <- Stream.eval(conns.update(_ + (conn.remoteAddress -> conn)))
                _    <- conn.reads().through(pipe).to(conn.writes()).onFinalize(conns.update(_ - conn.remoteAddress).void)
              } yield ()
            })
      }
      .parJoin(maxConcurrent)
      .handleErrorWith(e => Stream.eval[F, Unit](F.delay(log.error(e)(s"server error: ${e}"))))
}

object TcpServerBuilder {
  def apply[F[_]: ConcurrentEffect, A: Codec: RequestId](implicit T: Timer[F]) = new TcpServerBuilder[F, A]
}
