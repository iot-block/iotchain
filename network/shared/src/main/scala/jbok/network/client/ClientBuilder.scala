package jbok.network.client

import java.net.URI

import cats.effect.ConcurrentEffect
import fs2._
import scodec.Codec

abstract class ClientBuilder[F[_]: ConcurrentEffect, A: Codec] {
  def connect(to: URI,
              pipe: Pipe[F, A, A],
              reuseAddress: Boolean = true,
              sendBufferSize: Int = 256 * 1024,
              receiveBufferSize: Int = 256 * 1024,
              keepAlive: Boolean = true,
              noDelay: Boolean = true): Stream[F, Unit]
}
