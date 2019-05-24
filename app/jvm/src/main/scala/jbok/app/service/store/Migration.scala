package jbok.app.service.store

import cats.effect.Sync
import cats.implicits._
import jbok.core.config.DatabaseConfig
import org.flywaydb.core.Flyway

object Migration {
  private val flyway = new Flyway()
  flyway.setLocations("db/mysql", "db/sqlite")

  def migrate[F[_]: Sync](config: DatabaseConfig): F[Unit] =
    Sync[F].delay {
      config.driver match {
        case "org.sqlite.JDBC" => flyway.setLocations("db/sqlite")
        case _                 => flyway.setLocations("db/mysql")
      }
      flyway.setDataSource(config.url, config.user, config.password)
      flyway.migrate()
    }.void
}
