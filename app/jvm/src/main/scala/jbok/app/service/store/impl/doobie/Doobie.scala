package jbok.app.service.store.impl.doobie

import cats.effect.{ContextShift, IO, Resource}
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import doobie.util.transactor.Transactor

object Doobie {
  private val log                           = jbok.common.log.getLogger("doobie")
  private val driver                        = "org.sqlite.JDBC"
  private def forPath(path: String): String = s"jdbc:sqlite:${path}"

  private val logHandler = {
    import doobie.util.log._
    LogHandler {
      case Success(s, a, e1, e2) =>
        log.trace(s"""Successful Statement Execution:
                     | ${s}
                     | arguments = [${a.mkString(", ")}]
                     |   elapsed = ${e1.toMillis} ms exec + ${e2.toMillis} ms processing (${(e1 + e2).toMillis} ms total)
      """.stripMargin)

      case ProcessingFailure(s, a, e1, e2, t) =>
        log.error(s"""Failed Resultset Processing:
                     | ${s}
                     | arguments = [${a.mkString(", ")}]
                     |   elapsed = ${e1.toMillis} ms exec + ${e2.toMillis} ms processing (failed) (${(e1 + e2).toMillis} ms total)
                     |   failure = ${t.getMessage}
      """.stripMargin)

      case ExecFailure(s, a, e1, t) =>
        log.error(s"""Failed Statement Execution:
                     | ${s}
                     | arguments = [${a.mkString(", ")}]
                     |   elapsed = ${e1.toMillis} ms exec (failed)
                     |   failure = ${t.getMessage}
      """.stripMargin)
    }
  }

  def newXa(dbPath: Option[String])(implicit cs: ContextShift[IO]): Resource[IO, Transactor[IO]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](32) // our connect EC
      te <- ExecutionContexts.cachedThreadPool[IO]    // our transaction EC
      xa <- HikariTransactor.newHikariTransactor[IO](
        driver,
        forPath(dbPath.getOrElse(":memory:")),
        "", // username
        "", // password
        ce, // await connection here
        te // execute JDBC operations here
      )
    } yield xa
}
