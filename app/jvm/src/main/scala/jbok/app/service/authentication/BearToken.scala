package jbok.app.service.authentication

import tsec.authentication.{BearerTokenAuthenticator, SecuredRequestHandler, TSecBearerToken, TSecTokenSettings}
import tsec.common.SecureRandomId

import scala.concurrent.duration._

object BearToken {
  val bearerTokenStore =
    DummyBackingStore.apply[SecureRandomId, TSecBearerToken[Long]](s => SecureRandomId.coerce(s.id))

  val userStore = DummyBackingStore.apply[Long, User](_.id)

  val settings = TSecTokenSettings(
    expiryDuration = 10.minutes,
    maxIdle = None
  )

  val authenticator = BearerTokenAuthenticator(
    bearerTokenStore,
    userStore,
    settings
  )

  val Auth = SecuredRequestHandler(authenticator)
}
