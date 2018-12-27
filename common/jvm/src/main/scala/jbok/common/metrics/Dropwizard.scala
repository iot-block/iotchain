package jbok.common.metrics

import java.util.concurrent.TimeUnit

import cats.effect.{Async, Resource, Sync}
import cats.implicits._
import com.codahale.metrics.jmx.JmxReporter
import com.codahale.metrics.{ConsoleReporter, MetricRegistry}
import fs2._

import scala.concurrent.duration.FiniteDuration

object Dropwizard {
  def apply[F[_]](_registry: MetricRegistry)(implicit F: Sync[F]): Metrics[F] = new Metrics[F] {
    override type Registry = MetricRegistry

    override val registry: Registry = _registry

    override def time(name: String, labels: List[String])(nanos: Long): F[Unit] = F.delay {
      registry
        .timer(name)
        .update(nanos, TimeUnit.NANOSECONDS)
    }

    override def gauge(name: String, labels: List[String])(delta: Double): F[Unit] = F.delay {
      val counter = registry.counter(name)
      if (delta >= 0.0) {
        counter.inc(delta.toLong)
      } else {
        counter.dec(-delta.toLong)
      }
    }

    override def current(name: String, labels: String*)(current: Double): F[Unit] = F.delay {
      val counter = registry.counter(name)
      val count= counter.getCount
      if (current > count) {
        counter.inc(current.toLong - count)
      } else {
        counter.dec(count - current.toLong)
      }
    }
  }

  def consoleReporter[F[_]](registry: MetricRegistry, interval: FiniteDuration)(
      implicit F: Async[F]): Stream[F, Unit] = {
    val resource = Resource.make {
      F.delay {
        ConsoleReporter
          .forRegistry(registry)
          .convertRatesTo(TimeUnit.SECONDS)
          .convertDurationsTo(TimeUnit.MILLISECONDS)
          .build()
      }
    } { r =>
      F.delay(r.close())
    }

    Stream.resource(resource).evalMap(r => F.delay(r.start(interval.toSeconds, TimeUnit.SECONDS)) >> F.never)
  }

  def jmxReporter[F[_]](registry: MetricRegistry)(implicit F: Async[F]): Stream[F, Unit] = {
    val resource = Resource.make {
      F.delay {
        JmxReporter
          .forRegistry(registry)
          .convertRatesTo(TimeUnit.SECONDS)
          .convertDurationsTo(TimeUnit.MILLISECONDS)
          .build()
      }
    } { r =>
      F.delay(r.close())
    }
    Stream.resource(resource).evalMap(r => F.delay(r.start()) >> F.never)
  }
}
