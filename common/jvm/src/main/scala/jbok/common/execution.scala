package jbok.common
import java.lang.Thread.UncaughtExceptionHandler
import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.spi.AsynchronousChannelProvider
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, ThreadFactory}

import cats.effect.{ContextShift, IO, Timer}

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

object execution {
  implicit val EC: ExecutionContext = ExecutionContext.Implicits.global

  implicit val T: Timer[IO] = IO.timer(EC)

  implicit val CS: ContextShift[IO] = IO.contextShift(EC)

  implicit val AG: AsynchronousChannelGroup =
    AsynchronousChannelProvider
      .provider()
      .openAsynchronousChannelGroup(8, namedThreadFactory("fs2-ag-tcp", true))

  def namedThreadFactory(threadPrefix: String, daemon: Boolean, exitJvmOnFatalError: Boolean = true): ThreadFactory =
    new ThreadFactory {
      val defaultFactory = Executors.defaultThreadFactory()
      val idx            = new AtomicInteger(0)
      def newThread(r: Runnable): Thread = {
        val t = defaultFactory.newThread(r)
        t.setDaemon(daemon)
        t.setName(s"$threadPrefix-${idx.incrementAndGet()}")
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
