package jbok.app

import cats.effect.{ExitCode, IO, IOApp}
import jbok.core.config.Configs.FullNodeConfig

object AppServer extends IOApp {
  val config: FullNodeConfig = FullNodeConfig.apply("", 10000)

  override def run(args: List[String]): IO[ExitCode] =
    for {
      fullNode <- FullNode.forConfig(config)
      _        <- fullNode.server.stream.compile.drain
    } yield ExitCode.Success
}
