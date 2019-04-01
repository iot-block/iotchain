package jbok.app.service.store.impl.quill

import cats.effect.{IO, Resource}
import com.typesafe.config.ConfigFactory
import io.getquill._
import cats.implicits._

import scala.collection.JavaConverters._

object Quill {
  private def forPath(dbPath: String) =
    ConfigFactory.parseMap(
      Map(
        "driverClassName" -> "org.sqlite.JDBC",
        "jdbcUrl"         -> s"jdbc:sqlite:${dbPath}"
      ).asJava
    )

  def newCtx(dbPath: Option[String]): Resource[IO, SqliteJdbcContext[Literal.type]] =
    Resource.make {
      val config = forPath(dbPath.getOrElse(":memory:"))
      IO(new SqliteJdbcContext(Literal, config))
    } { ctx =>
      IO(println("close quill")) >> IO(ctx.close()) >> IO(println("closed quill"))
    }
}
