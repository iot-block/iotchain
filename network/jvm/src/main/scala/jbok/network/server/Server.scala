package jbok.network.server

import java.net.InetSocketAddress

import fs2._

final case class Server[F[_]](bind: InetSocketAddress, stream: Stream[F, Unit])
