package jbok.network.client

import java.net.InetSocketAddress

import cats.effect.ConcurrentEffect
import cats.implicits._
import fs2._
import fs2.async.mutable.{Queue, Signal}
import fs2.async.{Promise, Ref}
import jbok.network.common.{RequestId, RequestMethod}
import scodec.Codec

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import jbok.network.execution._

class Client[F[_], A](
    private val stream: Stream[F, Unit],
    private val in: Queue[F, A],
    private val out: Queue[F, A],
    private val signal: Signal[F, Boolean],
    val promises: Ref[F, Map[String, Promise[F, A]]],
    val queues: Ref[F, Map[String, Queue[F, A]]]
)(implicit F: ConcurrentEffect[F],
  C: Codec[A],
  I: RequestId[A],
  M: RequestMethod[A],
  S: Scheduler,
  EC: ExecutionContext) {

  def write(a: A): F[Unit] =
    out.enqueue1(a)

  def writes: Sink[F, A] =
    out.enqueue

  def request(a: A, timeout: Option[FiniteDuration] = None): F[A] =
    for {
      promise <- fs2.async.promise[F, A]
      _       <- promises.modify(_ + (I.id(a).get -> promise))
      _       <- write(a)
      resp    <- promise.get
    } yield resp

  def read: F[A] =
    in.dequeue1

  def reads: Stream[F, A] =
    in.dequeue

  def getOrCreateQueue(methodOpt: Option[String]): F[Queue[F, A]] =
    methodOpt match {
      case None => F.pure(in)
      case Some(method) =>
        queues.get.flatMap(_.get(method) match {
          case Some(queue) =>
            F.pure(queue)
          case None =>
            for {
              queue <- fs2.async.boundedQueue[F, A](32)
              _     <- queues.modify(_ + (method -> queue))
            } yield queue
        })
    }

  def start: F[Unit] =
    signal.get.flatMap {
      case false => F.unit
      case true  => signal.set(false) *> F.start(stream.interruptWhen(signal).compile.drain).void
    }

  def stop: F[Unit] =
    signal.set(true)
}

object Client {
  def apply[F[_], A: Codec](
      builder: ClientBuilder[F, A],
      to: InetSocketAddress,
      maxQueued: Int = 32
  )(implicit F: ConcurrentEffect[F],
    I: RequestId[A],
    M: RequestMethod[A],
  ): F[Client[F, A]] =
    for {
      in       <- fs2.async.boundedQueue[F, A](maxQueued)
      out      <- fs2.async.boundedQueue[F, A](maxQueued)
      signal   <- fs2.async.signalOf[F, Boolean](true)
      promises <- fs2.async.refOf[F, Map[String, Promise[F, A]]](Map.empty)
      queues   <- fs2.async.refOf[F, Map[String, Queue[F, A]]](Map.empty)
    } yield {

      def getOrCreateQueue(methodOpt: Option[String]): F[Queue[F, A]] =
        methodOpt match {
          case None =>
            F.pure(in)
          case Some(method) =>
            queues.get.flatMap(_.get(method) match {
              case Some(queue) => F.pure(queue)
              case None =>
                for {
                  queue <- fs2.async.boundedQueue[F, A](32)
                  _     <- queues.modify(_ + (method -> queue))
                } yield queue
            })
        }

      val pipe: Pipe[F, A, A] = { input =>
        val inbound: Stream[F, Unit] =
          input.evalMap { a =>
            I.id(a) match {
              case None =>
                getOrCreateQueue(M.method(a)).flatMap(_.enqueue1(a))
              case Some(id) =>
                promises.get.flatMap(_.get(id) match {
                  case Some(promise) => promise.complete(a) *> promises.modify(_ - id).void
                  case None          => getOrCreateQueue(M.method(a)).flatMap(_.enqueue1(a))
                })
            }
          }

        val outbound: Stream[F, A] = out.dequeue
        outbound.concurrently(inbound)
      }
      val stream: Stream[F, Unit] = builder.connect(to, pipe).onFinalize(signal.set(true))
      new Client[F, A](stream, in, out, signal, promises, queues)
    }
}
