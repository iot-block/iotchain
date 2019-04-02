package jbok.app.service.middleware

import cats.effect.IO
import org.http4s.HttpApp
import org.http4s.server.middleware.GZip

object GzipMiddleware {
  def apply(httpApp: HttpApp[IO]): HttpApp[IO] = GZip[IO, IO](httpApp)
}
