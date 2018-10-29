package jbok.common

import java.lang.Thread.UncaughtExceptionHandler
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, ThreadFactory}

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

trait ExecutionPlatform extends execution {
  override def executionContext: ExecutionContext =
    ExecutionContext.Implicits.global

  override def asyncChannelGroup: AsynchronousChannelGroup =
    AsynchronousChannelGroup.withThreadPool(
      Executors.newCachedThreadPool(ExecutionPlatform.mkThreadFactory("AG", daemon = true)))
}

object ExecutionPlatform {
  def mkThreadFactory(name: String, daemon: Boolean, exitJvmOnFatalError: Boolean = true): ThreadFactory =
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
}
