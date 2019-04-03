package jbok.app.service.store.impl.quill

import cats.effect.{IO, Resource}
import com.typesafe.config.ConfigFactory
import io.getquill._
import io.getquill.context.monix.Runner

import scala.collection.JavaConverters._

object Quill {
  private def forUrl(dbUrl: String) =
    ConfigFactory.parseMap(
      Map(
        "driverClassName" -> "org.sqlite.JDBC",
        "jdbcUrl"         -> dbUrl
      ).asJava
    )

  type Ctx = SqliteMonixJdbcContext[Literal.type]

  def newCtx(dbUrl: String): Resource[IO, Ctx] =
    Resource.make {
      val config = forUrl(dbUrl)
      IO(new SqliteMonixJdbcContext(Literal, config, Runner.default))
    } { ctx =>
      IO(ctx.close())
    }
}
