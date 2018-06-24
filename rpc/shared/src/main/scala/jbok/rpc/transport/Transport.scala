package jbok.rpc.transport

import cats.effect.Effect
import cats.implicits._
import fs2.async.mutable.{Signal, Topic}
import fs2.async.{Promise, Ref}
import fs2.{Scheduler, _}
import jbok.rpc.HostPort

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, _}

abstract class Transport[F[_], Id, A](val addr: HostPort)(implicit F: Effect[F], ec: ExecutionContext) {
  val promises: Ref[F, Map[Id, Promise[F, A]]]

  val stopWhenTrue: Signal[F, Boolean]

  def send(req: A): F[Unit]

  def start: F[Unit]

  def stop: F[Unit]

  def request(id: Id, req: A, timeout: FiniteDuration = 3.seconds)(implicit S: Scheduler): F[A] =
    for {
      promise <- fs2.async.promise[F, A]
      _ <- promises.modify(_ + (id -> promise))
      _ <- send(req)
      resp <- promise.timedGet(timeout, S).map(_.get)
      _ <- promises.modify(_ - id)
    } yield resp

  def isUp: F[Boolean] = stopWhenTrue.get.map(!_)
}

abstract class DuplexTransport[F[_], Id, A](addr: HostPort)(implicit F: Effect[F], ec: ExecutionContext)
    extends Transport[F, Id, A](addr) {
  val events: Topic[F, Option[A]]

  def parse(x: A): F[(Option[Id], A)]

  def subscribe(maxQueued: Int = 32): Stream[F, A] = events.subscribe(maxQueued).unNone

  def handle(x: A) =
    for {
      (idOpt, x) <- parse(x)
      _ <- idOpt match {
        case Some(id) =>
          for {
            m <- promises.get
            _ <- m.get(id) match {
              case Some(p) => p.complete(x)
              case None => ??? // request
            }
          } yield ()
        case None => events.publish1(x.some)
      }
    } yield ()
}
