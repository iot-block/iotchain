package jbok.common.metrics
import cats.effect.Resource

trait MetricsSyntax {
  implicit def effectMetricsOps[F[_], A](fa: F[A]): EffectMetricsOps[F, A] =
    new EffectMetricsOps[F, A](fa)

  implicit def resourceMetricsOps[F[_], A](res: Resource[F, A]): ResourceMetricsOps[F, A] =
    new ResourceMetricsOps[F, A](res)
}
