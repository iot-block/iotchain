package jbok.app.service.middleware

import cats.effect.{Clock, IO}
import org.http4s.HttpApp
import org.http4s.server.middleware.Throttle

import scala.concurrent.duration.FiniteDuration

object ThrottleMiddleware {
  private implicit val clock = Clock.create[IO]

  def apply(amount: Int, duration: FiniteDuration)(httpApp: HttpApp[IO]): IO[HttpApp[IO]] =
    Throttle(amount, duration)(httpApp)
}
