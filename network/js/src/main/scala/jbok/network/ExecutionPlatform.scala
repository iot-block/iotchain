package jbok.network

import java.nio.channels.AsynchronousChannelGroup
import scala.concurrent.duration.FiniteDuration
import scala.scalajs.js.timers._

import fs2.Scheduler
import fs2.io.udp.AsynchronousSocketGroup

import scala.concurrent.ExecutionContext

trait ExecutionPlatform extends execution {
  override def executionContext: ExecutionContext =
    ExecutionContext.Implicits.global

  override def scheduler: Scheduler = new Scheduler {
    override def scheduleOnce(delay: FiniteDuration)(thunk: => Unit): () => Unit = {
      val handle = setTimeout(delay)(thunk)
      () =>
      { clearTimeout(handle) }
    }
    override def scheduleAtFixedRate(period: FiniteDuration)(thunk: => Unit): () => Unit = {
      val handle = setInterval(period)(thunk)
      () =>
      { clearInterval(handle) }
    }
    override def toString = "Scheduler"
  }

  override def asyncChannelGroup: AsynchronousChannelGroup = null

  override def asyncSocketGroup: AsynchronousSocketGroup = null
}
