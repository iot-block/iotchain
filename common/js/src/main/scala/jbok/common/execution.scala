package jbok.common
import cats.effect.{ContextShift, IO, Timer}

import scala.concurrent.ExecutionContext

object execution {
  implicit val EC: ExecutionContext = ExecutionContext.Implicits.global

  implicit val T: Timer[IO] = IO.timer(EC)

  implicit val CS: ContextShift[IO] = IO.contextShift(EC)
}
