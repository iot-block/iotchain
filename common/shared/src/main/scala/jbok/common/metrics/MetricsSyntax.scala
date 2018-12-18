package jbok.common.metrics

trait MetricsSyntax {
  implicit def effectMetricsOps[F[_], A](fa: F[A]): MetricsOps[F, A] =
    new MetricsOps[F, A](fa)
}
