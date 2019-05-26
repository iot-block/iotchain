package jbok.app

import java.nio.file.Paths

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import jbok.app.txgen.ValidTxGen
import jbok.common.config.Config
import jbok.core.config.FullConfig
import monocle.macros.syntax.lens._

object TxGeneratorMain extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    Config[IO].read[FullConfig](Paths.get(args.head)).flatMap { config =>
      AppModule.resource[IO](config.lens(_.persist.driver).set("memory")).use { objects =>
        val generator = objects.get[ValidTxGen[IO]]
        generator.stream.compile.drain.as(ExitCode.Success)
      }
    }
}
