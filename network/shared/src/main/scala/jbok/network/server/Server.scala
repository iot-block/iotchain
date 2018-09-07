package jbok.network.server

import java.net.InetSocketAddress

import cats.effect.ConcurrentEffect
import cats.implicits._
import fs2._
import fs2.async.Ref
import fs2.async.mutable.{Queue, Signal}
import jbok.network.Connection
import scodec.Codec

import scala.concurrent.ExecutionContext
import jbok.network.execution._

class Server[F[_], A](
    val stream: Stream[F, Unit],
    val connections: Ref[F, Map[InetSocketAddress, Connection[F, A]]], // active connections
    val queue: Queue[F, (InetSocketAddress, A)], // outbound queue of (remote address -> connection)
    val signal: Signal[F, Boolean]
)(implicit F: ConcurrentEffect[F], C: Codec[A], EC: ExecutionContext) {
  def start: F[Unit] =
    signal.get.flatMap {
      case false => F.unit
      case true  => signal.set(false) *> F.start(stream.interruptWhen(signal).compile.drain).void
    }

  def stop: F[Unit] =
    signal.set(true)

  def write(remote: InetSocketAddress, a: A): F[Unit] =
    queue.enqueue1(remote -> a)

  def writes: Sink[F, (InetSocketAddress, A)] =
    queue.enqueue
}

object Server {
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
      conns  <- fs2.async.refOf[F, Map[InetSocketAddress, Connection[F, A]]](Map.empty)
      queue  <- fs2.async.boundedQueue[F, (InetSocketAddress, A)](maxQueued)
      signal <- fs2.async.signalOf[F, Boolean](true)
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
        .onFinalize(signal.set(true))
      new Server[F, A](stream, conns, queue, signal)
    }
}
