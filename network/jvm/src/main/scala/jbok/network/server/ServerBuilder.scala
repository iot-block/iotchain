package jbok.network.server

import java.net.InetSocketAddress

import cats.effect.ConcurrentEffect
import fs2._
import fs2.async.Ref
import jbok.network.Connection
import scodec.Codec

/**
  * A server builder can listen to a local bind address and
  * return a stream that can emit zero to many accepted connection streams
  *
  * @tparam F effect type
  * @tparam A payload data type
  */
abstract class ServerBuilder[F[_]: ConcurrentEffect, A: Codec] {
  def listen(bind: InetSocketAddress,
             pipe: Pipe[F, A, A],
             conns: Ref[F, Map[InetSocketAddress, Connection[F, A]]],
             maxConcurrent: Int = Int.MaxValue,
             maxQueued: Int = 0,
             reuseAddress: Boolean = true,
             receiveBufferSize: Int = 256 * 1024): Stream[F, Unit]
}
