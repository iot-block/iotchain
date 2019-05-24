package jbok.app

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import jbok.core.config.NetworkBuilder.Topology
import jbok.core.config.{CoreConfig, NetworkBuilder}

object NetworkBuilderMain extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    AppModule.resource[IO]().use { objects =>
      val config = objects
        .get[CoreConfig]

      val builder = NetworkBuilder(config)
        .withN(4)
        .withAlloc()
        .withTopology(Topology.Star)
        .withMiners(1)

      builder.dump.as(ExitCode.Success)
    }
}
