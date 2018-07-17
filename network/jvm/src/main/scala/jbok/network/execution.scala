package jbok.network
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.Executors

import fs2.Scheduler
import spinoco.fs2.http.util

import scala.concurrent.ExecutionContext

object execution {

  implicit val EC: ExecutionContext = ExecutionContext.Implicits.global

  implicit val Sch: Scheduler = Scheduler.fromScheduledExecutorService(
    Executors.newScheduledThreadPool(1, util.mkThreadFactory("fs2-scheduler", daemon = true)))

  implicit val AG: AsynchronousChannelGroup =
    AsynchronousChannelGroup.withThreadPool(Executors.newCachedThreadPool(util.mkThreadFactory("AG", daemon = true)))
}
