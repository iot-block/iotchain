package jbok.network

import java.nio.channels.AsynchronousChannelGroup

import fs2.Scheduler
import fs2.io.udp.AsynchronousSocketGroup

import scala.concurrent.ExecutionContext

trait execution {
  def executionContext: ExecutionContext

  def scheduler: Scheduler

  def asyncChannelGroup: AsynchronousChannelGroup

  def asyncSocketGroup: AsynchronousSocketGroup
}

object execution extends ExecutionPlatform {
  implicit val EC: ExecutionContext = executionContext

  implicit val Sch: Scheduler = scheduler

  implicit val AG: AsynchronousChannelGroup = asyncChannelGroup

  implicit val AsyncSocketGroup: AsynchronousSocketGroup = asyncSocketGroup
}

