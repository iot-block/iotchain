package jbok.app

import java.nio.file.Paths

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import jbok.core.config.{CoreConfig, GenesisBuilder, NetworkBuilder}
import jbok.core.models.Address
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import monocle.macros.syntax.lens._

object NetworkBuilderMain extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    def randomKP: KeyPair =
      Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync()

    val miner0 = randomKP
    val miner1 = randomKP
    val miner2 = randomKP

    val coinbase0 = Address(randomKP)
    val coinbase1 = Address(randomKP)
    val coinbase2 = Address(randomKP)

    val genesis = GenesisBuilder()
      .withChainId(10)
      .addAlloc(Address(randomKP), BigInt("1" + "0" * 30))
      .addMiner(Address(miner0))
      .addMiner(Address(miner1))
      .addMiner(Address(miner2))
      .build

    AppModule.resource[IO]().use { objects =>
      val config = objects
        .get[CoreConfig]
        .lens(_.genesis)
        .set(genesis)

      val builder = NetworkBuilder(config)
        .withBlockPeriod(1000)
        .withTrustStorePath("/Users/xsy/dbj/jbok/bin/generated/ca/cacert.jks")
        .addMinerNode(miner0.secret, coinbase0, Paths.get("/Users/xsy/.jbok/node-0"), "127.0.0.2", "/Users/xsy/dbj/jbok/bin/generated/certs/cert0/server.jks")
        .addMinerNode(miner1.secret, coinbase1, Paths.get("/Users/xsy/.jbok/node-1"), "127.0.0.3", "/Users/xsy/dbj/jbok/bin/generated/certs/cert1/server.jks")
        .addMinerNode(miner2.secret, coinbase2, Paths.get("/Users/xsy/.jbok/node-2"), "127.0.0.4", "/Users/xsy/dbj/jbok/bin/generated/certs/cert2/server.jks")

      builder.dump.as(ExitCode.Success)
    }
  }
}
