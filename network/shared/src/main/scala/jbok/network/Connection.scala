package jbok.network

import cats.effect.concurrent.{Deferred, Ref}
import cats.effect.implicits._
import cats.effect.{ConcurrentEffect, Fiber, Resource}
import cats.implicits._
import fs2._
import fs2.concurrent.{Queue, SignallingRef}
import jbok.network.common.RequestId
import scodec.Codec

/**
  * wraps a raw connection that can read and write bytes
  * support request response pattern by [[RequestId]] and [[cats.effect.concurrent.Deferred]]
  */
final case class Connection[F[_], A: Codec: RequestId](
    stream: Stream[F, Unit],
    in: Queue[F, A],
    out: Queue[F, A],
    promises: Ref[F, Map[String, Deferred[F, A]]],
    incoming: Boolean,
    haltWhenTrue: SignallingRef[F, Boolean]
)(implicit F: ConcurrentEffect[F]) {
  def write(a: A): F[Unit] =
    out.enqueue1(a)

  def sink: Sink[F, A] =
    out.enqueue

  def read: F[A] =
    in.dequeue1

  def reads: Stream[F, A] =
    in.dequeue.interruptWhen(haltWhenTrue)

  def request(a: A): F[A] = {
    val resource = Resource.make[F, (String, Deferred[F, A])] {
      for {
        id      <- F.delay(RequestId[A].id(a))
        promise <- Deferred[F, A]
        _       <- promises.update(_ + (id -> promise))
      } yield (id, promise)
    } {
      case (id, _) =>
        promises.update(_ - id)
    }

    resource.use {
      case (_, promise) =>
        write(a) *> promise.get
    }
  }

  def start: F[Fiber[F, Unit]] =
    haltWhenTrue.set(false) *> stream.interruptWhen(haltWhenTrue).compile.drain.start

  def close: F[Unit] =
    haltWhenTrue.set(true)
}
