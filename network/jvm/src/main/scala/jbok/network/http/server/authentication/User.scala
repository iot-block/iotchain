package jbok.network.http.server.authentication

import cats.MonadError
import tsec.authorization.AuthorizationInfo

final case class User(id: Long, role: Role = Role.Customer)
object User {
  implicit def authRole[F[_]](implicit F: MonadError[F, Throwable]): AuthorizationInfo[F, Role, User] =
    new AuthorizationInfo[F, Role, User] {
      def fetchInfo(u: User): F[Role] = F.pure(u.role)
    }
}
