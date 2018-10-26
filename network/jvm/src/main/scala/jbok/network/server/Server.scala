package jbok.network.server

import java.net.InetSocketAddress

import cats.effect.ConcurrentEffect
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2._
import fs2.concurrent.{Queue, SignallingRef}
import jbok.network.Connection
import jbok.common.execution._
import scodec.Codec

import scala.concurrent.ExecutionContext

class Server[F[_], A](
    val stream: Stream[F, Unit],
    val connections: Ref[F, Map[InetSocketAddress, Connection[F]]], // active connections
    val queue: Queue[F, (InetSocketAddress, A)], // outbound queue of (remote address -> connection)
    val signal: SignallingRef[F, Boolean]
)(implicit F: ConcurrentEffect[F], C: Codec[A], EC: ExecutionContext) {
  private[this] val log = org.log4s.getLogger

  def start: F[Unit] =
    for {
      _ <- signal.set(false)
      _ <- F.start(stream.interruptWhen(signal).compile.drain).void
      _ <- F.delay(log.info(s"start server"))
    } yield ()

  def stop: F[Unit] =
    signal.set(true)

  def write(remote: InetSocketAddress, a: A): F[Unit] =
    queue.enqueue1(remote -> a)

  def writes: Sink[F, (InetSocketAddress, A)] =
    queue.enqueue
}

object Server {
  private[this] val log = org.log4s.getLogger

  def apply[F[_], A](
      builder: ServerBuilder[F, A],
      bind: InetSocketAddress,
      pipe: Pipe[F, A, A],
      maxConcurrent: Int = Int.MaxValue,
      maxQueued: Int = 32,
      reuseAddress: Boolean = true,
      receiveBufferSize: Int = 256 * 1024
  )(implicit F: ConcurrentEffect[F], C: Codec[A]): F[Server[F, A]] =
    for {
      conns  <- Ref.of[F, Map[InetSocketAddress, Connection[F]]](Map.empty)
      queue  <- Queue.bounded[F, (InetSocketAddress, A)](maxQueued)
      signal <- SignallingRef[F, Boolean](true)
    } yield {
      val pipeWithPush: Pipe[F, A, A] = { input =>
        input
          .through(pipe)
          .concurrently(queue.dequeue.evalMap {
            case (remote, a) =>
              conns.get.flatMap(_.get(remote) match {
                case Some(conn) => conn.write(a)
                case None       => F.unit
              })
          })
      }
      val stream: Stream[F, Unit] = builder
        .listen(bind, pipeWithPush, conns, maxConcurrent, maxQueued, reuseAddress, receiveBufferSize)
        .onFinalize(signal.set(true) *> F.delay(log.info(s"on finalize")))

      new Server[F, A](stream, conns, queue, signal)
    }
}
