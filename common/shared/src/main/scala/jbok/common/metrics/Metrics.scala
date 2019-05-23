package jbok.common.metrics

import cats.effect.{Concurrent, Resource, Sync, Timer}
import fs2._

import scala.concurrent.duration._
import cats.implicits._

trait EffectMetrics[F[_]] { self: Metrics[F] =>
  def observed[A](name: String, labels: String*)(fa: F[A])(implicit F: Sync[F], T: Timer[F]): F[A] =
    for {
      start   <- T.clock.monotonic(NANOSECONDS)
      attempt <- fa.attempt
      end     <- T.clock.monotonic(NANOSECONDS)
      elapsed = end - start
      a <- attempt match {
        case Left(e) =>
          self.observe(name, "failure" :: labels.toList: _*)(elapsed) >> F.raiseError(e)
        case Right(a) =>
          self.observe(name, "success" :: labels.toList: _*)(elapsed).as(a)
      }
    } yield a

  def monitored[A](name: String, labels: String*)(res: Resource[F, A])(implicit F: Sync[F]): Resource[F, A] = {
    val r = Resource {
      for {
        _ <- self.inc(name, labels: _*)(1.0)
      } yield () -> self.dec(name, labels: _*)(1.0)
    }

    r.flatMap(_ => res)
  }
}

trait StreamMetrics[F[_]] { self: Metrics[F] =>
  // observe events occur in the stream
  def observePipe[A](name: String, labels: String*)(implicit F: Concurrent[F]): Pipe[F, A, Unit] =
    _.chunks.through(observeChunkPipe[A](name, labels: _*))

  def observeChunkPipe[A](name: String, labels: String*)(implicit F: Concurrent[F]): Pipe[F, Chunk[A], Unit] =
    _.evalMap(c => self.observe(name, labels: _*)(c.size))
}

trait Metrics[F[_]] extends EffectMetrics[F] with StreamMetrics[F] {
  type Registry

  def registry: Registry

  // accumulate, e.g. the number of requests served, tasks completed, or errors.
  def acc(name: String, labels: String*)(n: Double = 1.0): F[Unit]

  // increase, e.g. the current memory usage, queue size, or active requests.
  def inc(name: String, labels: String*)(n: Double = 1.0): F[Unit]

  // decrease, e.g. the current memory usage, queue size, or active requests.
  def dec(name: String, labels: String*)(n: Double = 1.0): F[Unit]

  // equivalent to inc(name, labels)(delta)
  def set(name: String, labels: String*)(n: Double): F[Unit]

  // e.g. the request response latency, or the size of the response body
  def observe(name: String, labels: String*)(n: Double): F[Unit]
}

object Metrics {
  val METRIC_PREFIX = "jbok"
  val TIMER_SUFFIX  = "seconds"
  val GAUGE_SUFFIX  = "active"

  sealed trait NoopRegistry

  object NoopRegistry extends NoopRegistry

  def nop[F[_]: Sync]: Metrics[F] = new Metrics[F] {
    override type Registry = NoopRegistry
    override def registry: Registry                                         = NoopRegistry
    override def acc(name: String, labels: String*)(n: Double): F[Unit]     = Sync[F].unit
    override def inc(name: String, labels: String*)(n: Double): F[Unit]     = Sync[F].unit
    override def dec(name: String, labels: String*)(n: Double): F[Unit]     = Sync[F].unit
    override def set(name: String, labels: String*)(n: Double): F[Unit]     = Sync[F].unit
    override def observe(name: String, labels: String*)(n: Double): F[Unit] = Sync[F].unit
  }
}
