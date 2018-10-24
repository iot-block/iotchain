package jbok.network.client

import java.net.{InetSocketAddress, URI}

import cats.effect.{ConcurrentEffect, Timer}
import fs2._
import jbok.network.common.{RequestId, TcpUtil}
import jbok.common.execution._
import scodec.Codec

class TcpClientBuilder[F[_], A: Codec: RequestId](implicit F: ConcurrentEffect[F], T: Timer[F])
    extends ClientBuilder[F, A] {
  private[this] val log = org.log4s.getLogger

  override def connect(
      to: URI,
      pipe: Pipe[F, A, A],
      reuseAddress: Boolean,
      sendBufferSize: Int,
      receiveBufferSize: Int,
      keepAlive: Boolean,
      noDelay: Boolean
  ): Stream[F, Unit] = {
    val isa = new InetSocketAddress(to.getHost, to.getPort)
    Stream
      .resource(
        fs2.io.tcp
          .client[F](isa, reuseAddress, sendBufferSize, receiveBufferSize, keepAlive, noDelay))
      .flatMap(socket => {
        log.debug(s"connected to ${socket}")
        for {
          conn <- Stream.eval(TcpUtil.socketToConnection(socket, false))
          _    <- conn.reads().through(pipe).to(conn.writes())
        } yield ()
      })
      .handleErrorWith(e => Stream.eval(F.delay(log.error(s"client error: ${e.printStackTrace()}"))))
  }
}

object TcpClientBuilder {
  def apply[F[_]: ConcurrentEffect, A: Codec: RequestId](implicit T: Timer[F]) = new TcpClientBuilder[F, A]
}
