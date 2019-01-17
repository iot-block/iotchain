package jbok.network.client

import java.net.URI
import java.nio.channels.AsynchronousChannelGroup

import cats.effect._

object Client {
  def apply[F[_]](uri: URI)(
      implicit F: ConcurrentEffect[F],
      cs: ContextShift[F],
      T: Timer[F],
      AG: AsynchronousChannelGroup
  ): F[Client[F]] = uri.getScheme match {
    case "ws"  => WsClient[F](uri)
    case "tcp" => TcpClient[F](uri)
    case x     => F.raiseError(new Exception(s"protocol $x is not supported"))
  }
}
