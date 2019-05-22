package jbok.network.http.server.middleware

import java.time.{Duration, Instant}

import cats.data.{Kleisli, OptionT}
import cats.effect.Sync
import jbok.network.http.server.authentication.HMAC
import org.http4s.headers.Authorization
import org.http4s.util.CaseInsensitiveString
import org.http4s.{AuthScheme, Credentials, HttpRoutes, Request, Response, Status}
import tsec.mac.jca.{HMACSHA256, MacSigningKey}

import scala.concurrent.duration.{FiniteDuration, _}

sealed abstract class HmacAuthError(val message: String) extends Exception(message)
object HmacAuthError {
  case object NoAuthHeader     extends HmacAuthError("Could not find an Authorization header")
  case object NoDatetimeHeader extends HmacAuthError("Could not find an X-Datetime header")
  case object BadMAC           extends HmacAuthError("Bad MAC")
  case object InvalidMacFormat extends HmacAuthError("The MAC is not a valid Base64 string")
  case object InvalidDatetime  extends HmacAuthError("The datetime is not a valid UTC datetime string")
  case object Timeout          extends HmacAuthError("The request time window is closed")
}

object HmacAuthMiddleware {
  val defaultDuration: FiniteDuration = 5.minutes

  private def verifyFromHeader[F[_]](
      req: Request[F],
      key: MacSigningKey[HMACSHA256],
      duration: FiniteDuration
  ): Either[HmacAuthError, Unit] =
    for {
      authHeader <- req.headers
        .get(Authorization)
        .flatMap { t =>
          t.credentials match {
            case Credentials.Token(scheme, token) if scheme == AuthScheme.Bearer =>
              Some(token)
            case _ => None
          }
        }
        .toRight(HmacAuthError.NoAuthHeader)
      datetimeHeader <- req.headers
        .get(CaseInsensitiveString("X-Datetime"))
        .toRight(HmacAuthError.NoDatetimeHeader)
      instant <- HMAC.http.verifyFromHeader(
        req.method.name,
        req.uri.renderString,
        datetimeHeader.value,
        authHeader,
        key
      )
      _ <- Either.cond(
        Instant.now().isBefore(instant.plus(Duration.ofNanos(duration.toNanos))),
        (),
        HmacAuthError.Timeout
      )
    } yield ()

  def apply[F[_]: Sync](key: MacSigningKey[HMACSHA256], duration: FiniteDuration = defaultDuration)(routes: HttpRoutes[F]): HttpRoutes[F] =
    Kleisli { req: Request[F] =>
      verifyFromHeader(req, key, duration) match {
        case Left(error) => OptionT.some[F](Response[F](Status.Forbidden).withEntity(error.message))
        case Right(_)    => routes(req)
      }
    }
}
