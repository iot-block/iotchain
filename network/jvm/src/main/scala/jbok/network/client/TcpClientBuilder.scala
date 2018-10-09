package jbok.network.client

import java.net.{InetSocketAddress, URI}

import cats.effect.ConcurrentEffect
import fs2._
import jbok.network.common.TcpUtil
import jbok.network.execution._
import scodec.Codec

class TcpClientBuilder[F[_], A: Codec](implicit F: ConcurrentEffect[F]) extends ClientBuilder[F, A] {
  private[this] val log = org.log4s.getLogger

  override def connect(to: URI,
                       pipe: Pipe[F, A, A],
                       reuseAddress: Boolean,
                       sendBufferSize: Int,
                       receiveBufferSize: Int,
                       keepAlive: Boolean,
                       noDelay: Boolean): Stream[F, Unit] = {
    val isa = new InetSocketAddress(to.getHost, to.getPort)
    fs2.io.tcp
      .client[F](isa, reuseAddress, sendBufferSize, receiveBufferSize, keepAlive, noDelay)
      .flatMap(socket => {
        log.debug(s"connected to ${socket}")
        val conn = TcpUtil.socketToConnection(socket)
        conn.reads().through(pipe).to(conn.writes())
      })
      .handleErrorWith(e => Stream.eval(F.delay(log.error(s"client error: ${e.printStackTrace()}"))))
  }
}

object TcpClientBuilder {
  def apply[F[_]: ConcurrentEffect, A: Codec] = new TcpClientBuilder[F, A]
}
