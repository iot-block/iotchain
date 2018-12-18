package jbok.common.metrics

import cats.effect.{ExitCase, Sync, Timer}
import fs2._
import cats.implicits._

import scala.concurrent.duration._

trait EffectMetrics[F[_]] { self: Metrics[F] =>
  def time[A](name: String, labels: String*)(fa: F[A])(implicit F: Sync[F], T: Timer[F]): F[A] =
    for {
      start   <- T.clock.monotonic(NANOSECONDS)
      attempt <- fa.attempt
      end     <- T.clock.monotonic(NANOSECONDS)
      elapsed = end - start
      a <- attempt match {
        case Left(e) =>
          self.time(name, "failure" :: labels.toList)(elapsed) >> F.raiseError(e)
        case Right(a) =>
          self.time(name, "success" :: labels.toList)(elapsed).as(a)
      }
    } yield a

  def gauge[A](name: String, labels: String*)(fa: F[A])(implicit F: Sync[F]): F[A] =
    for {
      _       <- self.gauge(name, labels.toList)(1.0)
      attempt <- fa.attempt
      a <- attempt match {
        case Left(e) =>
          self.gauge(name, labels.toList)(-1.0) >> F.raiseError(e)
        case Right(a) =>
          self.gauge(name, labels.toList)(-1.0).as(a)
      }
    } yield a
}

trait StreamMetrics[F[_]] { self: Metrics[F] =>
  def timeStream[A](name: String, labels: String*)(
      stream: Stream[F, A])(implicit F: Sync[F], T: Timer[F]): Stream[F, A] =
    for {
      start <- Stream.eval(T.clock.monotonic(NANOSECONDS))
      a <- stream.onFinalizeCase {
        case ExitCase.Completed =>
          T.clock.monotonic(NANOSECONDS).flatMap(end => self.time(name, "complete" :: labels.toList)(end - start))
        case ExitCase.Canceled =>
          T.clock.monotonic(NANOSECONDS).flatMap(end => self.time(name, "canceled" :: labels.toList)(end - start))
        case ExitCase.Error(_) =>
          T.clock.monotonic(NANOSECONDS).flatMap(end => self.time(name, "error" :: labels.toList)(end - start))
      }
    } yield a

  def gaugeStream[A](name: String, labels: String*)(stream: Stream[F, A])(implicit F: Sync[F]): Stream[F, A] =
    Stream.eval_(self.gauge(name, labels.toList)(1.0)) ++ stream.onFinalizeCase {
      case ExitCase.Completed => self.gauge(name, labels.toList)(-1)
      case ExitCase.Canceled  => self.gauge(name, labels.toList)(-1)
      case ExitCase.Error(_)  => self.gauge(name, labels.toList)(-1)
    }
}

trait Metrics[F[_]] extends EffectMetrics[F] with StreamMetrics[F] {
  def time(name: String, labels: List[String])(elapsed: Long): F[Unit]

  def gauge(name: String, labels: List[String])(delta: Double): F[Unit]
}

object Metrics extends MetricsPlatform {
  def default[F[_]: Sync]: F[Metrics[F]] = _default
}
