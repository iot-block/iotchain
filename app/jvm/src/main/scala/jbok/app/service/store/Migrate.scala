package jbok.app.service.store

import cats.effect.IO
import org.flywaydb.core.Flyway

object Migrate {
  private def forPath(path: String): String = s"jdbc:sqlite:${path}"

  def migrate(dbPath: Option[String], dbUser: String = "", dbPass: String = ""): IO[Unit] = IO {
    val dbUrl  = forPath(dbPath.getOrElse(":memory:"))
    val flyway = new Flyway
    flyway.setDataSource(dbUrl, dbUser, dbPass)
    flyway.migrate()
  }
}
