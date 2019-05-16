package jbok.app

import cats.effect.{ExitCode, IO, IOApp}
import jbok.app.txgen.ValidTxGen
import cats.implicits._

object TxGenerator extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    AppModule.resource[IO]().use { objects =>
      val generator = objects.get[ValidTxGen[IO]]
      generator.stream.compile.drain.as(ExitCode.Success)
    }
}
