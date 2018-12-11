package jbok.app

import cats.effect.{ExitCode, IO}
import fs2._
import jbok.core.config.Configs.FullNodeConfig

object AppServer extends StreamApp {
  val config: FullNodeConfig = ConfigGenerator.withIdentityAndPort("my-node", 10000)

  override def run(args: List[String]): IO[ExitCode] =
    runStream {
      for {
        fullNode <- Stream.resource(FullNode.forConfig(config))
        _        <- fullNode.server.stream
      } yield ()
    }
}
