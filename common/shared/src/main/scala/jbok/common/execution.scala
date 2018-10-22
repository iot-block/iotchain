package jbok.common

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.{ContextShift, IO, Timer}
import cats.effect.IO.{timer, contextShift}
import fs2.io.udp.AsynchronousSocketGroup

import scala.concurrent.ExecutionContext

trait execution {
  def executionContext: ExecutionContext

  def asyncChannelGroup: AsynchronousChannelGroup

  def asyncSocketGroup: AsynchronousSocketGroup
}

object execution extends ExecutionPlatform {
  implicit val EC: ExecutionContext = executionContext

  implicit val AG: AsynchronousChannelGroup = asyncChannelGroup

  implicit val T: Timer[IO] = timer(EC)

  implicit val CS: ContextShift[IO] = contextShift(EC)

  implicit val AsyncSocketGroup: AsynchronousSocketGroup = asyncSocketGroup
}
