package jbok.network.http.server.middleware

import cats.effect.{Clock, Sync}
import org.http4s.HttpApp
import org.http4s.server.middleware.Throttle

import scala.concurrent.duration.FiniteDuration

object ThrottleMiddleware {
  def apply[F[_]: Sync](amount: Int, duration: FiniteDuration)(httpApp: HttpApp[F])(implicit clock: Clock[F]): F[HttpApp[F]] =
    Throttle(amount, duration)(httpApp)
}
