package jbok.app.service.middleware
import cats.Id
import cats.effect.IO
import cats.implicits._
import jbok.JbokSpec
import jbok.app.service.authentication.HMAC
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.{AuthScheme, Credentials, Header, HttpRoutes, Method, Request, Status, Uri}
import tsec.mac.jca.HMACSHA256
import java.time.Instant
import scala.concurrent.duration._
import jbok.common.execution._

import org.http4s.headers.Authorization

class HmacAuthMiddlewareSpec extends JbokSpec {
  "HmacAuthMiddleware" should {
    val key = HMACSHA256.generateKey[Id]

    val routes = HttpRoutes.of[IO] {
      case GET -> Root / "ping" => Ok("pong")
    }

    val service = routes.orNotFound
    val req     = Request[IO](uri = Uri.uri("/ping"))
    service.run(req).unsafeRunSync().status shouldBe Status.Ok
    val middleware    = new HmacAuthMiddleware(key)
    val authedService = middleware(routes).orNotFound

    "403 if no Authorization header" in {
      val resp = authedService.run(req).unsafeRunSync()
      val text = resp.bodyAsText.compile.foldMonoid.unsafeRunSync()
      resp.status shouldBe Status.Forbidden
      text shouldBe HmacAuthError.NoAuthHeader.message
    }

    "403 if no X-Datetime header" in {
      val signature = HMAC.http.signForHeader("GET", "/ping", Instant.now().toString, key).unsafeRunSync()
      val req =
        Request[IO](uri = Uri.uri("/ping")).putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, signature)))
      val resp = authedService.run(req).unsafeRunSync()
      val text = resp.bodyAsText.compile.foldMonoid.unsafeRunSync()
      resp.status shouldBe Status.Forbidden
      text shouldBe HmacAuthError.NoDatetimeHeader.message
    }

    "403 if time window is closed" in {
      val middleware    = new HmacAuthMiddleware(key, 2.seconds)
      val authedService = middleware(routes).orNotFound
      val now           = Instant.now()
      val signature     = HMAC.http.signForHeader("GET", "/ping", now.toString, key).unsafeRunSync()
      val req =
        Request[IO](uri = Uri.uri("/ping"))
          .putHeaders(
            Authorization(Credentials.Token(AuthScheme.Bearer, signature)),
            Header("X-Datetime", now.toString)
          )

      val resp = authedService.run(req).unsafeRunSync()
      resp.status shouldBe Status.Ok

      IO.sleep(3.seconds).unsafeRunSync()
      val resp2 = authedService.run(req).unsafeRunSync()
      val text  = resp2.bodyAsText.compile.foldMonoid.unsafeRunSync()
      resp2.status shouldBe Status.Forbidden
      text shouldBe HmacAuthError.Timeout.message
    }
  }
}
