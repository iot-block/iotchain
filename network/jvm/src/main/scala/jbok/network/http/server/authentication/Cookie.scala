package jbok.network.http.server.authentication

import java.util.UUID

import cats.effect.IO
import tsec.authentication.{
  AuthEncryptedCookie,
  AuthenticatedCookie,
  BackingStore,
  EncryptedCookieAuthenticator,
  SecuredRequestHandler,
  SignedCookieAuthenticator,
  TSecCookieSettings
}
import tsec.cipher.symmetric.jca.{AES128GCM, SecretKey}
import tsec.mac.jca.{HMACSHA256, MacSigningKey}

import scala.concurrent.duration._

object Cookie {
  val cookieStore: BackingStore[IO, UUID, AuthenticatedCookie[HMACSHA256, Long]] =
    DummyBackingStore[UUID, AuthenticatedCookie[HMACSHA256, Long]](_.id)

  val userStore: BackingStore[IO, Long, User] = DummyBackingStore[Long, User](_.id)

  val settings: TSecCookieSettings = TSecCookieSettings(
    cookieName = "jbok-auth",
    secure = false,
    expiryDuration = 10.minutes, // Absolute expiration time
    maxIdle = None // Rolling window expiration. Set this to a Finiteduration if you intend to have one
  )

  class Unencrypted(key: MacSigningKey[HMACSHA256]) {
    val cookieAuth =
      SignedCookieAuthenticator(
        settings,
        cookieStore,
        userStore,
        key
      )

    val Auth = SecuredRequestHandler(cookieAuth)

  }

  class Encrypted(key: SecretKey[AES128GCM]) {
    implicit val encryptor   = AES128GCM.genEncryptor[IO]
    implicit val gcmstrategy = AES128GCM.defaultIvStrategy[IO]

    val cookieStore: BackingStore[IO, UUID, AuthEncryptedCookie[AES128GCM, Long]] =
      DummyBackingStore[UUID, AuthEncryptedCookie[AES128GCM, Long]](_.id)

    val userStore: BackingStore[IO, Long, User] = DummyBackingStore[Long, User](_.id)

    val settings: TSecCookieSettings = TSecCookieSettings(
      cookieName = "jbok-auth",
      secure = false,
      expiryDuration = 10.minutes, // Absolute expiration time
      maxIdle = None // Rolling window expiration. Set this to a FiniteDuration if you intend to have one
    )

    val authWithBackingStore = //Instantiate a stateful authenticator
      EncryptedCookieAuthenticator.withBackingStore(
        settings,
        cookieStore,
        userStore,
        key
      )

    object stateless {
      val authenticator = EncryptedCookieAuthenticator.stateless(
        settings,
        userStore,
        key
      )

      val Auth = SecuredRequestHandler(authenticator)
    }

    object stateful {
      val authenticator = EncryptedCookieAuthenticator.withBackingStore(
        settings,
        cookieStore,
        userStore,
        key
      )

      val Auth = SecuredRequestHandler(authenticator)
    }
  }
}
