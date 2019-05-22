package jbok.network.http.server.middleware

import cats.effect.{Clock, Effect, Sync}
import cats.implicits._
import jbok.common.metrics.MetricsPlatform
import org.http4s.HttpRoutes
import org.http4s.metrics.prometheus.{Prometheus, PrometheusExportService}
import org.http4s.server.middleware

object MetricsMiddleware {
  def exportService[F[_]](implicit F: Sync[F]): F[HttpRoutes[F]] =
    for {
      _ <- PrometheusExportService.addDefaults[F](MetricsPlatform.globalRegistry)
    } yield PrometheusExportService.service[F](MetricsPlatform.globalRegistry)

  def apply[F[_]](routes: HttpRoutes[F])(implicit F: Effect[F], clock: Clock[F]): F[HttpRoutes[F]] =
    Prometheus[F](MetricsPlatform.globalRegistry, "jbok_http_server").map { metricsOps =>
      middleware.Metrics[F](metricsOps)(routes)
    }
}
