package jbok.network.http.client.middleware

import cats.effect.{Sync, Timer}
import org.http4s.client.Client
import org.http4s.client.middleware.{Retry, RetryPolicy}

import scala.concurrent.duration._

object RetryMiddleware {
  private def defaultPolicy[F[_]] =
    RetryPolicy[F](RetryPolicy.exponentialBackoff(maxWait = 60.seconds, maxRetry = 3))

  def apply[F[_]](retryPolicy: RetryPolicy[F] = defaultPolicy[F])(client: Client[F])(implicit F: Sync[F], T: Timer[F]): Client[F] =
    Retry[F](retryPolicy)(client)
}
