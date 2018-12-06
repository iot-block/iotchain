package jbok.network.client

import java.net.{InetSocketAddress, URI}
import java.nio.channels.AsynchronousChannelGroup

import cats.effect._
import cats.implicits._
import jbok.network.common.{RequestId, TcpUtil}
import scodec.Codec

object TcpClient {

  def apply[F[_], A: Codec: RequestId](
      uri: URI,
      maxQueued: Int = 64,
      maxBytes: Int = 256 * 1024
  )(
      implicit F: ConcurrentEffect[F],
      cs: ContextShift[F],
      T: Timer[F],
      AG: AsynchronousChannelGroup
  ): F[Client[F, A]] = {
    val to = new InetSocketAddress(uri.getHost, uri.getPort)
    TcpUtil
      .socketToConnection[F, A](fs2.io.tcp.Socket.client[F](to, keepAlive = true, noDelay = true), false)
      .map(conn => Client(conn.stream, conn.in, conn.out, conn.promises, uri, conn.haltWhenTrue))
  }
}
