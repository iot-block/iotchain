package jbok.app.service.store

import cats.effect.Sync
import jbok.app.config.DatabaseConfig
import org.flywaydb.core.Flyway

object Migration {
  private val flyway = new Flyway

  def migrate[F[_]: Sync](config: DatabaseConfig): F[Unit] = Sync[F].delay {
    flyway.setDataSource(config.url, config.user, config.password)
    flyway.migrate()
  }
}
