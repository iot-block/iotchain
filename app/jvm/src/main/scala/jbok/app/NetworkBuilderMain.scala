package jbok.app

import java.nio.file.Paths

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import jbok.core.config.NetworkBuilder.Topology
import jbok.core.config.{CoreConfig, GenesisBuilder, NetworkBuilder}
import jbok.core.models.Address
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import monocle.macros.syntax.lens._

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
