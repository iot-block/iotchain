package jbok.network.client

import java.net.{InetSocketAddress, URI}
import java.nio.channels.AsynchronousChannelGroup

import cats.effect._
import jbok.network.common.TcpUtil

object TcpClient {
  def apply[F[_]](uri: URI)(
      implicit F: ConcurrentEffect[F],
      cs: ContextShift[F],
      T: Timer[F],
      AG: AsynchronousChannelGroup
  ): F[Client[F]] = {
    val to = new InetSocketAddress(uri.getHost, uri.getPort)
    TcpUtil
      .socketToConnection[F](fs2.io.tcp.Socket.client[F](
                               to,
                               keepAlive = true,
                               noDelay = true,
                               sendBufferSize = 4 * 1024 * 1024,
                               receiveBufferSize = 4 * 1024 * 1024
                             ),
                             false)
  }
}
