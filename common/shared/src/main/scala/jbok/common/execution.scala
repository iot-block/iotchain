package jbok.common

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.IO.{contextShift, timer}
import cats.effect.{ContextShift, IO, Timer}

import scala.concurrent.ExecutionContext

trait execution {
  def executionContext: ExecutionContext

  def asyncChannelGroup: AsynchronousChannelGroup
}

object execution extends ExecutionPlatform {
  implicit val EC: ExecutionContext = executionContext

  implicit val AG: AsynchronousChannelGroup = asyncChannelGroup

  implicit val T: Timer[IO] = timer(EC)

  implicit val CS: ContextShift[IO] = contextShift(EC)
}
