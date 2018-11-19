package jbok.network.client

import java.net.URI

import cats.effect._
import cats.effect.concurrent.{Deferred, Ref}
import fs2._
import fs2.concurrent.{Queue, SignallingRef}
import jbok.network.Connection
import jbok.network.common.RequestId
import scodec.Codec

object Client {
  def apply[F[_], A](
      stream: Stream[F, Unit],
      in: Queue[F, A],
      out: Queue[F, A],
      promises: Ref[F, Map[String, Deferred[F, A]]],
      uri: URI,
      haltWhenTrue: SignallingRef[F, Boolean]
  )(implicit F: ConcurrentEffect[F], C: Codec[A], I: RequestId[A]): Client[F, A] =
    Connection[F, A](stream, in, out, promises, false, haltWhenTrue)
}
