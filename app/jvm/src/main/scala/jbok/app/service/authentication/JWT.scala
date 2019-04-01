package jbok.app.service.authentication
import cats.Id
import cats.effect.IO
import io.circe.{Decoder, ObjectEncoder}
import tsec.authentication.{AugmentedJWT, JWTAuthenticator, SecuredRequestHandler}
import tsec.common.SecureRandomId
import tsec.jwt.JWTClaims
import tsec.mac.jca.{HMACSHA256, MacSigningKey}
import io.circe.generic.auto._
import tsec.jws.mac.JWTMac

import scala.concurrent.duration._

object JWT {
  class Unbacked(key: MacSigningKey[HMACSHA256]) {
    private val userStore = DummyBackingStore.apply[Long, User](_.id)

    private val authenticator = JWTAuthenticator.unbacked.inBearerToken(
      expiryDuration = 10.minutes, //Absolute expiration time
      maxIdle = None,
      identityStore = userStore,
      signingKey = key
    )

    val Auth = SecuredRequestHandler(authenticator)
  }

  class Stateful(key: MacSigningKey[HMACSHA256]) {
    private val jwtStore =
      DummyBackingStore.apply[SecureRandomId, AugmentedJWT[HMACSHA256, Long]](s => SecureRandomId.coerce(s.id))
    private val userStore = DummyBackingStore.apply[Long, User](_.id)

    private val authenticator = JWTAuthenticator.backed.inBearerToken(
      expiryDuration = 10.minutes, //Absolute expiration time
      maxIdle = None,
      tokenStore = jwtStore,
      identityStore = userStore,
      signingKey = key
    )

    val Auth =
      SecuredRequestHandler(authenticator)
  }

  class Stateless(key: MacSigningKey[HMACSHA256]) {
    private val authenticator = JWTAuthenticator.pstateless.inBearerToken[IO, User, HMACSHA256](
      expiryDuration = 10.minutes,
      maxIdle = None,
      signingKey = key
    )

    val Auth =
      SecuredRequestHandler(authenticator)
  }

  final case class CustomClaim(suchChars: String, much32Bits: Int, so64Bits: Long)
  val claim = "JBOK"

  def withCustomClaims[A: ObjectEncoder: Decoder](
      key: String,
      value: A,
      duration: Option[FiniteDuration] = None
  ): IO[JWTClaims] =
    JWTClaims
      .withDuration[IO](expiration = duration).flatMap(_.withCustomFieldF[IO, A](key, value))

  def sign(claims: JWTClaims, key: MacSigningKey[HMACSHA256]): IO[JWTMac[HMACSHA256]] =
    for {
      jwt <- JWTMac.build[IO, HMACSHA256](claims, key) //You can sign and build a jwt object directly
    } yield jwt

  def signToString(claims: JWTClaims, key: MacSigningKey[HMACSHA256]): IO[String] =
    sign(claims, key).map(_.toEncodedString)

  def parseFromString(jwt: String, key: MacSigningKey[HMACSHA256]): IO[JWTMac[HMACSHA256]] =
    JWTMac.verifyAndParse[IO, HMACSHA256](jwt, key)

  def verifyFromString(jwt: String, key: MacSigningKey[HMACSHA256]): IO[Boolean] =
    JWTMac.verifyFromStringBool[IO, HMACSHA256](jwt, key)
}
