package jbok.network

import java.nio.channels.AsynchronousChannelGroup

import fs2.Scheduler
import fs2.io.udp.AsynchronousSocketGroup

import scala.concurrent.ExecutionContext

trait ExecutionPlatform extends execution {
  override def executionContext: ExecutionContext =
    ExecutionContext.Implicits.global

  override def scheduler: Scheduler = null

  override def asyncChannelGroup: AsynchronousChannelGroup = null

  override def asyncSocketGroup: AsynchronousSocketGroup = null
}
