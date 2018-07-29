package jbok.network.transport

import cats.effect.Effect
import cats.implicits._
import fs2.async.mutable.{Signal, Topic}
import fs2.async.{Promise, Ref}
import fs2.{Scheduler, _}
import jbok.network.NetAddress

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, _}

abstract class Transport[F[_], Id, A](val addr: NetAddress)(implicit F: Effect[F], ec: ExecutionContext) {
  val promises: Ref[F, Map[Id, Promise[F, A]]]

  val stopWhenTrue: Signal[F, Boolean]

  def send(req: A): F[Unit]

  def start: F[Unit]

  def stop: F[Unit]

  def request(id: Id, req: A, timeout: FiniteDuration)(implicit S: Scheduler): F[Option[A]] =
    for {
      promise <- fs2.async.promise[F, A]
      _ <- promises.modify(_ + (id -> promise))
      _ <- send(req)
      resp <- promise.timedGet(timeout, S)
      _ <- promises.modify(_ - id)
    } yield resp

  def isUp: F[Boolean] = stopWhenTrue.get.map(!_)
}

abstract class DuplexTransport[F[_], Id, A](addr: NetAddress)(implicit F: Effect[F], ec: ExecutionContext)
    extends Transport[F, Id, A](addr) {

  val topics: Ref[F, Map[String, Topic[F, Option[A]]]]

  def parseId(x: A): F[(Option[Id], A)]

  def parseMethod(x: A): F[(String, A)]

  def getOrCreateTopic(method: String): F[Topic[F, Option[A]]] =
    for {
      m <- topics.get
      topic <- m.get(method) match {
        case Some(t) => t.pure[F]
        case None =>
          for {
            t <- fs2.async.topic[F, Option[A]](None)
            _ <- topics.modify(_ + (method -> t))
          } yield t
      }
    } yield topic

  def subscribe(method: String = "", maxQueued: Int = 32): Stream[F, A] =
    for {
      m <- Stream.eval(topics.get)
      topic <- Stream.eval(m.get(method) match {
        case Some(t) => t.pure[F]
        case None =>
          for {
            t <- fs2.async.topic[F, Option[A]](None)
            _ <- topics.modify(_ + (method -> t))
          } yield t
      })
      events <- topic.subscribe(maxQueued).unNone
    } yield events

  def handle(x: A) =
    for {
      (idOpt, x) <- parseId(x)
      _ <- idOpt match {
        case Some(id) =>
          for {
            m <- promises.get
            _ <- m.get(id) match {
              case Some(p) => p.complete(x)
              case None    => ??? // request
            }
          } yield ()

        case None =>
          for {
            (method, x) <- parseMethod(x)
            topic <- getOrCreateTopic(method)
            _ <- topic.publish1(x.some)
          } yield ()
      }
    } yield ()
}
