package jbok.app.service.middleware

import cats.effect.IO
import io.prometheus.client.CollectorRegistry
import org.http4s.HttpRoutes
import org.http4s.metrics.prometheus.Prometheus
import org.http4s.server.middleware

import scala.concurrent.ExecutionContext

object MetricsMiddleware {
  implicit private val clock = IO.timer(ExecutionContext.global).clock

  val registry = new CollectorRegistry()

  def apply(routes: HttpRoutes[IO]): IO[HttpRoutes[IO]] =
    Prometheus[IO](registry, "jbok_http_server").map { metricsOps =>
      middleware.Metrics[IO](metricsOps)(routes)
    }
}
