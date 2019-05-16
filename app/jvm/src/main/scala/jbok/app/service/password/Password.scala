package jbok.app.service.password

import cats.effect.Sync
import tsec.passwordhashers._
import tsec.passwordhashers.jca._

object Password {
  def hash[F[_]: Sync](password: Array[Char]): F[PasswordHash[SCrypt]] =
    SCrypt.hashpw[F](password)

  def verify[F[_]: Sync](password: Array[Char], hash: PasswordHash[SCrypt]): F[Boolean] =
    SCrypt.checkpwBool[F](password, hash)
}
