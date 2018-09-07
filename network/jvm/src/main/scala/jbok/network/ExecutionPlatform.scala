package jbok.network

import java.lang.Thread.UncaughtExceptionHandler
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, ThreadFactory}

import fs2.Scheduler
import fs2.io.udp.AsynchronousSocketGroup

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

trait ExecutionPlatform extends execution {
  private def mkThreadFactory(name: String, daemon: Boolean, exitJvmOnFatalError: Boolean = true): ThreadFactory =
    new ThreadFactory {
      val idx            = new AtomicInteger(0)
      val defaultFactory = Executors.defaultThreadFactory()
      def newThread(r: Runnable): Thread = {
        val t = defaultFactory.newThread(r)
        t.setName(s"$name-${idx.incrementAndGet()}")
        t.setDaemon(daemon)
        t.setUncaughtExceptionHandler(new UncaughtExceptionHandler {
          def uncaughtException(t: Thread, e: Throwable): Unit = {
            ExecutionContext.defaultReporter(e)
            if (exitJvmOnFatalError) {
              e match {
                case NonFatal(_) => ()
                case fatal       => System.exit(-1)
              }
            }
          }
        })
        t
      }
    }

  override def executionContext: ExecutionContext =
    ExecutionContext.Implicits.global

  override def scheduler: Scheduler =
    Scheduler.fromScheduledExecutorService(
      Executors.newScheduledThreadPool(1, mkThreadFactory("fs2-scheduler", daemon = true)))

  override def asyncChannelGroup: AsynchronousChannelGroup =
    AsynchronousChannelGroup.withThreadPool(Executors.newCachedThreadPool(mkThreadFactory("AG", daemon = true)))

  override def asyncSocketGroup: AsynchronousSocketGroup =
    AsynchronousSocketGroup.apply()
}
