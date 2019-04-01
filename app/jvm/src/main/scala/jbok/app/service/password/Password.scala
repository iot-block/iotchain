package jbok.app.service.password

import cats.effect.IO
import tsec.passwordhashers._
import tsec.passwordhashers.jca._

object Password {
  def hash(password: Array[Char]): IO[PasswordHash[SCrypt]] =
    SCrypt.hashpw[IO](password)

  def verify(password: Array[Char], hash: PasswordHash[SCrypt]): IO[Boolean] =
    SCrypt.checkpwBool[IO](password, hash)
}
