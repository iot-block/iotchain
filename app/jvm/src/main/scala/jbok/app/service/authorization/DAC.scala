package jbok.app.service.authorization

import cats.data.OptionT
import cats.effect.IO
import jbok.app.service.authentication.User
import tsec.authentication.SecuredRequest
import tsec.authorization.{AuthGroup, Authorization}

object DAC {
  val basicDAC: Authorization[IO, User, Long] = new Authorization[IO, User, Long] {
    def fetchGroup: IO[AuthGroup[Long]] = IO.pure(AuthGroup(1L, 2L, 3L))

    def fetchOwner: IO[Long] = IO.pure(1L)

    def fetchAccess(u: SecuredRequest[IO, User, Long]): IO[Long] = IO.pure(u.identity.id)

    def isAuthorized(request: SecuredRequest[IO, User, Long]): OptionT[IO, SecuredRequest[IO, User, Long]] = ???
  }
}
