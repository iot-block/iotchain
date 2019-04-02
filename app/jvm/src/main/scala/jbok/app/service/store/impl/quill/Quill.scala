package jbok.app.service.store.impl.quill

import cats.effect.{IO, Resource}
import com.typesafe.config.ConfigFactory
import io.getquill._
import cats.implicits._
import io.getquill.context.monix.Runner

import scala.collection.JavaConverters._

object Quill {
  private def forPath(dbPath: String) =
    ConfigFactory.parseMap(
      Map(
        "driverClassName" -> "org.sqlite.JDBC",
        "jdbcUrl"         -> s"jdbc:sqlite:${dbPath}"
      ).asJava
    )

  type Ctx = SqliteMonixJdbcContext[Literal.type]

  def newCtx(dbPath: Option[String]): Resource[IO, Ctx] =
    Resource.make {
      val config = forPath(dbPath.getOrElse(":memory:"))
      IO(new SqliteMonixJdbcContext(Literal, config, Runner.default))
    } { ctx =>
      IO(println("close quill")) >> IO(ctx.close()) >> IO(println("closed quill"))
    }
}
