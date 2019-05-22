package jbok.network.http.server.middleware

import cats.Functor
import org.http4s.HttpApp
import org.http4s.server.middleware.GZip

object GzipMiddleware {
  def apply[F[_]: Functor](httpApp: HttpApp[F]): HttpApp[F] = GZip[F, F](httpApp)
}
