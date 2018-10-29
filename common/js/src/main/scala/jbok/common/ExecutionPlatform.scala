package jbok.common

import java.nio.channels.AsynchronousChannelGroup

import fs2.io.udp.AsynchronousSocketGroup

import scala.concurrent.ExecutionContext

trait ExecutionPlatform extends execution {
  override def executionContext: ExecutionContext =
    ExecutionContext.Implicits.global

  override def asyncChannelGroup: AsynchronousChannelGroup = null
}
