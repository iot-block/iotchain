package jbok.app.service.middleware
import cats.effect.{Concurrent, IO}
import org.http4s.HttpApp
import org.http4s.server.middleware.Logger

object LoggingMiddleware {
  def apply(httpApp: HttpApp[IO])(implicit F: Concurrent[IO]): HttpApp[IO] =
    Logger.httpApp(logHeaders = true, logBody = false)(httpApp)
}
