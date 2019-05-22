package jbok.network.http.server.authentication

import cats.Id
import cats.effect.IO
import io.circe.generic.auto._
import jbok.common.CommonSpec
import tsec.mac.jca.{HMACSHA256, MacSigningKey}

import scala.concurrent.duration._

class JWTSpec extends CommonSpec {
  val jwtKey: MacSigningKey[HMACSHA256]  = HMACSHA256.generateKey[Id]
  val jwtKey2: MacSigningKey[HMACSHA256] = HMACSHA256.generateKey[Id]
  val user                               = User(1L, Role.Seller)

  "JWT" should {
    "build custom claims" in {
      val claims = JWT.withCustomClaims("user", user).unsafeRunSync()
      println(claims)
      val jwt = JWT.sign(claims, jwtKey).unsafeRunSync()
      println(jwt.toEncodedString)
      val jwt2      = JWT.sign(claims, jwtKey2).unsafeRunSync()
      val verified  = JWT.verifyFromString(jwt.toEncodedString, jwtKey).unsafeRunSync()
      val verified2 = JWT.verifyFromString(jwt.toEncodedString, jwtKey2).unsafeRunSync()
      verified shouldBe true
      verified2 shouldBe false
    }

    "respect expiration" in {
      val p = for {
        claims   <- JWT.withCustomClaims("user", user, Some(2.seconds))
        jwt      <- JWT.sign(claims, jwtKey)
        verified <- JWT.verifyFromString(jwt.toEncodedString, jwtKey)
        _ = verified shouldBe true
        _         <- IO.sleep(2.seconds)
        verified2 <- JWT.verifyFromString(jwt.toEncodedString, jwtKey)
        _ = verified2 shouldBe false
      } yield ()

      p.unsafeRunSync()
    }
  }
}
