package jbok.app.service.store

import cats.effect.Sync
import cats.implicits._
import jbok.core.config.DatabaseConfig
import org.flywaydb.core.Flyway

object Migration {
  private val flyway = new Flyway

  def migrate[F[_]: Sync](config: DatabaseConfig): F[Unit] =
    Sync[F].delay {
      flyway.setDataSource(config.url, config.user, config.password)
      flyway.migrate()
    }.void
}
