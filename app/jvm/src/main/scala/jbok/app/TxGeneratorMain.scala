package jbok.app

import java.nio.file.Paths

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import jbok.app.txgen.TxGenerator
import jbok.common.config.Config
import jbok.core.api.JbokClientPlatform
import jbok.core.config.FullConfig
import jbok.core.mining.TxGen
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import monocle.macros.syntax.lens._

object TxGeneratorMain extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    Config[IO].read[FullConfig](Paths.get(args.head)).flatMap { config =>
      AppModule.resource[IO](config.lens(_.persist.driver).set("memory")).use { objects =>
        val config           = objects.get[FullConfig]
        implicit val chainId = config.genesis.chainId
        val keyPairs: List[KeyPair] = List(
          KeyPair(
            KeyPair.Public("a4991b82cb3f6b2818ce8fedc00ef919ba505bf9e67d96439b63937d24e4d19d509dd07ac95949e815b307769f4e4d6c3ed5d6bd4883af23cb679b251468a8bc"),
            KeyPair.Secret("1a3c21bb6e303a384154a56a882f5b760a2d166161f6ccff15fc70e147161788")
          )
        ) ++ Signature[ECDSA].generateKeyPair[IO]().replicateA(10).unsafeRunSync()

        JbokClientPlatform.resource[IO](config.service.uri).use { client =>
          for {
            txGen <- TxGen[IO](keyPairs, client)
            generator = new TxGenerator[IO](config, txGen, client)
            _ <- generator.stream.compile.drain
          } yield ExitCode.Success
        }
      }
    }
}
