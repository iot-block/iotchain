package jbok.app.service.store.doobie

import cats.effect.{Async, ContextShift, Resource}
import doobie._
import doobie.hikari.HikariTransactor
import jbok.core.config.DatabaseConfig

object Doobie {
  def xa[F[_]](config: DatabaseConfig)(implicit F: Async[F], cs: ContextShift[F]): Resource[F, Transactor[F]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[F](32) // our connect EC
      te <- ExecutionContexts.cachedThreadPool[F]    // our transaction EC
      xa <- HikariTransactor.newHikariTransactor[F](
        config.driver,
        config.url,
        config.user,     // username
        config.password, // password
        ce,              // await connection here
        te               // execute JDBC operations here
      )
    } yield xa
}
