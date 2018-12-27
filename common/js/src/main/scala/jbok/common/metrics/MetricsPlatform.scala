package jbok.common.metrics

import cats.effect.Sync

trait MetricsPlatform {
  // use no-op now
  def _default[F[_]: Sync]: F[Metrics[F]] = Metrics.nop
}
