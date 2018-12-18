package jbok.common.metrics

import cats.effect.Sync

trait MetricsPlatform {
  sealed trait NoopRegistry
  object NoopRegistry extends NoopRegistry

  // use no-op now
  def _default[F[_]: Sync]: F[Metrics[F]] = Sync[F].pure {
    new Metrics[F] {
      override type Registry = NoopRegistry

      override def registry: Registry = NoopRegistry

      override def time(name: String, labels: List[String])(elapsed: Long): F[Unit] = Sync[F].unit

      override def gauge(name: String, labels: List[String])(delta: Double): F[Unit] = Sync[F].unit
    }
  }
}
