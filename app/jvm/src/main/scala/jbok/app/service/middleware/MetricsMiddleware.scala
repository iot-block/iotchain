package jbok.app.service.middleware

import cats.effect.{Clock, Effect}
import cats.implicits._
import io.prometheus.client.CollectorRegistry
import org.http4s.HttpRoutes
import org.http4s.metrics.prometheus.Prometheus
import org.http4s.server.middleware

object MetricsMiddleware {
  val registry = new CollectorRegistry()

  def apply[F[_]](routes: HttpRoutes[F])(implicit F: Effect[F], clock: Clock[F]): F[HttpRoutes[F]] =
    Prometheus[F](registry, "jbok_http_server").map { metricsOps =>
      middleware.Metrics[F](metricsOps)(routes)
    }
}
