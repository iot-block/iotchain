package jbok.app.service.store

import cats.effect.IO
import org.flywaydb.core.Flyway

object Migration {
  private val flyway = new Flyway

  def migrate(dbUrl: String, dbUser: String = "", dbPass: String = ""): IO[Unit] = IO {
    flyway.setDataSource(dbUrl, dbUser, dbPass)
    flyway.migrate()
  }
}
