package jbok.common.metrics

import cats.effect.{Resource, Sync, Timer}

final class EffectMetricsOps[F[_], A](val fa: F[A]) extends AnyVal {
  def observed(name: String, labels: String*)(implicit F: Sync[F], T: Timer[F], M: Metrics[F]): F[A] =
    M.observed(name, labels: _*)(fa)
}

final class ResourceMetricsOps[F[_], A](val res: Resource[F, A]) extends AnyVal {
  def monitored(name: String, labels: String*)(implicit F: Sync[F], T: Timer[F], M: Metrics[F]): Resource[F, A] =
    M.monitored(name, labels: _*)(res)
}
