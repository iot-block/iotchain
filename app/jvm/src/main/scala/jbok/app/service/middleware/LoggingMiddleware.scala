package jbok.app.service.middleware

import cats.effect.Concurrent
import org.http4s.HttpApp
import org.http4s.server.middleware.Logger

object LoggingMiddleware {
  def apply[F[_]](httpApp: HttpApp[F])(implicit F: Concurrent[F]): HttpApp[F] =
    Logger.httpApp(logHeaders = true, logBody = false)(httpApp)
}
