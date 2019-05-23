package jbok.network.http.client.middleware

import cats.effect.{Clock, Sync}
import cats.implicits._
import jbok.common.metrics.PrometheusMetrics
import org.http4s.Request
import org.http4s.client.Client
import org.http4s.client.middleware.Metrics
import org.http4s.metrics.prometheus.Prometheus

object MetricsMiddleware {
  private def requestMethodClassifier[F[_]] = (r: Request[F]) => Some(r.method.toString.toLowerCase)

  def apply[F[_]](client: Client[F])(implicit F: Sync[F], clock: Clock[F]): F[Client[F]] =
    Prometheus[F](PrometheusMetrics.registry, "jbok_http_client").map(
      Metrics[F](_, requestMethodClassifier)(client)
    )
}
