package jbok.common.metrics

import cats.implicits._
import cats.effect.Sync
import io.prometheus.client.CollectorRegistry

trait MetricsPlatform {
  // use prometheus now
  def _default[F[_]: Sync]: F[Metrics[F]] =
    for {
      registry <- new CollectorRegistry().pure[F]
      metrics  = Prometheus(registry)
    } yield metrics
}
