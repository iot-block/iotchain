package jbok.common.metrics

import cats.effect.{Sync, Timer}

final class MetricsOps[F[_], A](val fa: F[A]) extends AnyVal {
  def timed(name: String, labels: String*)(implicit F: Sync[F], T: Timer[F], M: Metrics[F]): F[A] =
    M.time(name, labels: _*)(fa)

  def gauged(name: String, labels: String*)(implicit F: Sync[F], M: Metrics[F]): F[A] =
    M.gauge(name, labels: _*)(fa)
}