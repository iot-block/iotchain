package jbok.common.thread

import java.lang.Thread.UncaughtExceptionHandler
import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.spi.AsynchronousChannelProvider
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, ThreadFactory}

import cats.effect.{Resource, Sync}

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

object ThreadUtil {
  def named(threadPrefix: String, daemon: Boolean, exitJvmOnFatalError: Boolean = true): ThreadFactory =
    new ThreadFactory {
      val defaultThreadFactory = Executors.defaultThreadFactory()
      val idx                  = new AtomicInteger(0)
      def newThread(r: Runnable) = {
        val t = defaultThreadFactory.newThread(r)
        t.setDaemon(daemon)
        t.setName(s"$threadPrefix-${idx.incrementAndGet()}")
        t.setUncaughtExceptionHandler(new UncaughtExceptionHandler {
          def uncaughtException(t: Thread, e: Throwable): Unit = {
            ExecutionContext.defaultReporter(e)
            if (exitJvmOnFatalError) {
              e match {
                case NonFatal(_) => ()
                case _           => System.exit(-1)
              }
            }
          }
        })
        t
      }
    }

  def blockingThreadPool[F[_]](name: String)(implicit F: Sync[F]): Resource[F, ExecutionContext] =
    Resource(F.delay {
      val factory  = named(name, daemon = true)
      val executor = Executors.newCachedThreadPool(factory)
      val ec       = ExecutionContext.fromExecutor(executor)
      (ec, F.delay(executor.shutdown()))
    })

  def acg[F[_]](implicit F: Sync[F]): Resource[F, AsynchronousChannelGroup] =
    Resource(F.delay {
      val acg = acgUnsafe
      (acg, F.delay(acg.shutdownNow()))
    })

  def acgUnsafe: AsynchronousChannelGroup =
    AsynchronousChannelProvider
      .provider()
      .openAsynchronousChannelGroup(8, named("jbok-ag-tcp", daemon = true))

  lazy val acgGlobal: AsynchronousChannelGroup = acgUnsafe
}
