package jbok.app

import java.nio.file.Paths

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import jbok.core.CoreModule
import jbok.core.config.{GenesisBuilder, NetworkBuilder}
import jbok.core.models.Address
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}

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
//      .addMiner(Address(miner1))
//      .addMiner(Address(miner2))
      .build

    val config = CoreModule.testConfig.copy(genesis = genesis)

    val home = System.getProperty("user.home")
    val root = Paths.get(home).resolve(".jbok")

    val builder = NetworkBuilder(config)
      .withBlockPeriod(1000)
      .addNode(miner0, coinbase0, root.resolve("node-0"), "127.0.0.2")
      .addNode(miner1, coinbase1, root.resolve("node-1"), "127.0.0.3")
      .addNode(miner2, coinbase2, root.resolve("node-2"), "127.0.0.4")

    builder.dump.as(ExitCode.Success)
  }
}
