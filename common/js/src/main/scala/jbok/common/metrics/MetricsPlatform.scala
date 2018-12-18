package jbok.common.metrics

import cats.effect.Sync

trait MetricsPlatform {
  // use no-op now
  def _default[F[_]: Sync]: F[Metrics[F]] = Sync[F].pure {
    new Metrics[F] {
      override def time(name: String, labels: List[String])(elapsed: Long): F[Unit] = Sync[F].unit

      override def gauge(name: String, labels: List[String])(delta: Double): F[Unit] = Sync[F].unit
    }
  }
}
