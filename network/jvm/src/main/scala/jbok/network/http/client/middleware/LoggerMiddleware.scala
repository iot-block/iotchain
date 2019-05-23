package jbok.network.http.client.middleware

import cats.effect.Concurrent
import org.http4s.client.Client
import org.http4s.client.middleware.{RequestLogger, ResponseLogger}

object LoggerMiddleware {
  def apply[F[_]](logHeaders: Boolean = true, logBody: Boolean = true)(client: Client[F])(implicit F: Concurrent[F]): Client[F] =
    ResponseLogger.apply(logHeaders, logBody)(
      RequestLogger.apply(logHeaders, false)(
        client
      )
    )
}
