package jbok.network.http.server.middleware

import cats.effect.ConcurrentEffect
import org.http4s.HttpApp
import org.http4s.server.middleware.{CORS, CORSConfig}

import scala.concurrent.duration._

object CORSMiddleware {
  val defaultConfig: CORSConfig = CORSConfig(
    anyOrigin = true,
    allowCredentials = true,
    maxAge = 1.day.toSeconds,
    anyMethod = true
  )

  private def corsConfig(allowedOriginsList: List[String]) =
    if (allowedOriginsList.isEmpty) {
      defaultConfig
    } else {
      defaultConfig.copy(anyOrigin = false, allowedOrigins = allowedOriginsList.toSet)
    }

  def apply[F[_]](http: HttpApp[F], allowedOrigins: List[String])(implicit F: ConcurrentEffect[F]): HttpApp[F] =
    CORS(http, corsConfig(allowedOrigins))
}
