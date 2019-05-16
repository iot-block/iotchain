package jbok.app.service.middleware

import java.time.Instant

import cats.Id
import cats.effect.IO
import cats.implicits._
import jbok.common.CommonSpec
import jbok.app.service.authentication.HMAC
import org.http4s.dsl.io._
import org.http4s.headers.Authorization
import org.http4s.implicits._
import org.http4s.{AuthScheme, Credentials, Header, HttpRoutes, Request, Status, Uri}
import scodec.bits.ByteVector
import tsec.mac.jca.{HMACSHA256, MacSigningKey}

import scala.concurrent.duration._

class HmacAuthMiddlewareSpec extends CommonSpec {
  "HmacAuthMiddleware" should {
    val key = HMACSHA256.buildKey[Id](
      ByteVector.fromValidHex("70ea14ac30939a972b5a67cab952d6d7d474727b05fe7f9283abc1e505919e83").toArray
    )

    def sign(url: String): (String, String) = {
      val datetime  = Instant.now().toString
      val signature = HMAC.http.signForHeader("GET", url, datetime, key).unsafeRunSync()
      (signature, datetime)
    }

    val routes = HttpRoutes.of[IO] {
      case GET -> Root / "ping" => Ok("pong")
    }

    val service = routes.orNotFound
    val req     = Request[IO](uri = Uri.uri("/ping"))
    service.run(req).unsafeRunSync().status shouldBe Status.Ok
    val authedService = HmacAuthMiddleware(key)(routes).orNotFound

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
      val authedService = HmacAuthMiddleware(key, 2.seconds)(routes).orNotFound
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

    "helper" in {
      val (sig, date) = sign("/v1/blocks")
      println("Authorization", s"Bearer $sig")
      println("X-Datetime", date)
      println("Random key", ByteVector(MacSigningKey.toJavaKey[HMACSHA256](HMACSHA256.generateKey[Id]).getEncoded).toHex)
    }
  }
}
