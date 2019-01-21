package jbok.network

import java.util.UUID

import cats.effect._
import cats.effect.concurrent.{Deferred, Ref}
import cats.effect.implicits._
import cats.implicits._
import fs2._
import fs2.concurrent.{Queue, SignallingRef}
import jbok.codec.rlp.RlpCodec

final case class Connection[F[_]](
    stream: Stream[F, Unit],
    in: Queue[F, Message[F]],
    out: Queue[F, Message[F]],
    promises: Ref[F, Map[UUID, Deferred[F, Response[F]]]],
    incoming: Boolean,
    haltWhenTrue: SignallingRef[F, Boolean]
)(implicit F: Concurrent[F], CS: ContextShift[F]) {
  def write(message: Message[F]): F[Unit] =
    out.enqueue1(message)

  def sink: Sink[F, Message[F]] =
    out.enqueue

  def read: F[Message[F]] =
    in.dequeue1

  def reads: Stream[F, Message[F]] =
    in.dequeue.interruptWhen(haltWhenTrue)

  def request(req: Request[F]): F[Response[F]] =
    Resource
      .make[F, (UUID, Deferred[F, Response[F]])] {
        for {
          promise <- Deferred[F, Response[F]]
          _       <- promises.update(_ + (req.id -> promise))
        } yield (req.id, promise)
      } {
        case (id, _) =>
          promises.update(_ - id)
      }
      .use {
        case (_, promise) =>
          write(req) >> promise.get
      }

  def expect[A](req: Request[F])(implicit C: RlpCodec[A]): F[A] =
    request(req).flatMap(_.binaryBodyAs[A])

  def start: F[Fiber[F, Unit]] =
    stream.compile.drain.start

  def close: F[Unit] =
    haltWhenTrue.set(true)
}

object Connection {
  def dummy[F[_]](implicit F: Concurrent[F], CS: ContextShift[F]): F[Connection[F]] =
    for {
      in           <- Queue.bounded[F, Message[F]](1)
      out          <- Queue.bounded[F, Message[F]](1)
      promises     <- Ref.of[F, Map[UUID, Deferred[F, Response[F]]]](Map.empty)
      haltWhenTrue <- SignallingRef[F, Boolean](true)
      stream = Stream.empty.covaryAll[F, Unit]
    } yield Connection[F](stream, in, out, promises, true, haltWhenTrue)
}