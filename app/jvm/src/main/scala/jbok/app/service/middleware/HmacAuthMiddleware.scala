package jbok.app.service.middleware
import java.time.{Duration, Instant}

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import jbok.app.service.authentication.HMAC
import org.http4s.dsl.io._
import org.http4s.headers.Authorization
import org.http4s.util.CaseInsensitiveString
import org.http4s.{AuthScheme, Credentials, HttpRoutes, Request}
import tsec.mac.jca.{HMACSHA256, MacSigningKey}

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

sealed abstract class HmacAuthError(val message: String) extends Exception(message)
object HmacAuthError {
  case object NoAuthHeader     extends HmacAuthError("Could not find an Authorization header")
  case object NoDatetimeHeader extends HmacAuthError("Could not find an X-Datetime header")
  case object BadMAC           extends HmacAuthError("Bad MAC")
  case object InvalidMacFormat extends HmacAuthError("The MAC is not a valid Base64 string")
  case object InvalidDatetime  extends HmacAuthError("The datetime is not a valid UTC datetime string")
  case object Timeout          extends HmacAuthError("The request time window is closed")
}

class HmacAuthMiddleware(key: MacSigningKey[HMACSHA256], duration: FiniteDuration = 5.minutes) {
  private val javaDuration = Duration.ofNanos(duration.toNanos)

  def verifyFromHeader(req: Request[IO]): Either[HmacAuthError, Unit] =
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
      _ <- Either.cond(Instant.now().isBefore(instant.plus(javaDuration)), (), HmacAuthError.Timeout)
    } yield ()

  def apply(routes: HttpRoutes[IO]): HttpRoutes[IO] = Kleisli { req: Request[IO] =>
    verifyFromHeader(req) match {
      case Left(error) => OptionT.liftF(Forbidden(error.message))
      case Right(_)    => routes(req)
    }
  }
}
