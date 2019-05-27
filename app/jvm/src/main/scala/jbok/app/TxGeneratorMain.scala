package jbok.app

import java.nio.file.Paths

import cats.effect.{ExitCode, IO, IOApp}
import jbok.app.txgen.TxGenerator
import jbok.common.config.Config
import jbok.core.api.{JbokClient, JbokClientPlatform}
import jbok.core.config.FullConfig
import jbok.core.mining.TxGen
import jbok.crypto.signature.KeyPair
import monocle.macros.syntax.lens._

object TxGeneratorMain extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    Config[IO].read[FullConfig](Paths.get(args.head)).flatMap { config =>
      AppModule.resource[IO](config.lens(_.persist.driver).set("memory")).use { objects =>
        val config                  = objects.get[FullConfig]
        implicit val chainId        = config.genesis.chainId
        val client                  = objects.get[JbokClient[IO]]
        val keyPairs: List[KeyPair] = ???

        JbokClientPlatform.resource[IO](config.service.uri).use { cient =>
          for {
            txGen <- TxGen[IO](keyPairs, client)
            generator = new TxGenerator[IO](config, txGen, client)
            _ <- generator.stream.compile.drain
          } yield ExitCode.Success
        }
      }
    }
}
