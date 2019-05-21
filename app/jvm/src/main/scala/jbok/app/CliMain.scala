package jbok.app

import java.nio.file.Paths

import cats.effect.{ExitCode, IO, IOApp}
import javax.net.ssl.SSLContext
import jbok.app.cli.Cli
import jbok.common.config.Config
import jbok.common.log.{Level, Logger}
import jbok.core.api.JbokClientPlatform
import jbok.core.config.CoreConfig
import monocle.macros.syntax.lens._

import scala.concurrent.duration._

object CliMain extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    Config[IO].read[CoreConfig](Paths.get(args.head)).flatMap { config =>
      AppModule.resource[IO](config.lens(_.persist.driver).set("inmem")).use { objects =>
        val config = objects.get[CoreConfig]
        val ssl    = objects.get[Option[SSLContext]]
        JbokClientPlatform.resource[IO](config.service.uri, ssl).use { client =>
          val cli = new Cli[IO](client)
          for {
            _ <- Logger.setRootLevel[IO](Level.Error)
            _ <- cli.loop(5.seconds).compile.drain
          } yield ExitCode.Success
        }
      }
    }
}
