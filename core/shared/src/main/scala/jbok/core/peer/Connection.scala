package jbok.core.peer

import cats.effect.Effect
import cats.implicits._
import fs2.async.mutable.{Queue, Topic}
import jbok.core.messages.Message

import scala.concurrent.ExecutionContext

case class Connection[F[_]](
    inbound: Topic[F, Option[Message]],
    outbound: Queue[F, Message],
    close: F[Unit]
)(implicit F: Effect[F], EC: ExecutionContext)

object Connection {
  def apply[F[_]](close: F[Unit], maxQueued: Int = 32)(implicit F: Effect[F], EC: ExecutionContext): F[Connection[F]] =
    for {
      inbound <- fs2.async.topic[F, Option[Message]](None)
      outbound <- fs2.async.boundedQueue[F, Message](maxQueued)
    } yield Connection[F](inbound, outbound, close)
}
