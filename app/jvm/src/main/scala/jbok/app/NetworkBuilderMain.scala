package jbok.app

import java.nio.file.Paths

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import jbok.common.math.N
import jbok.core.CoreModule
import jbok.core.config.{GenesisBuilder, NetworkBuilder}
import jbok.core.models.{Address, ChainId}
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

    val alloc = KeyPair(
      KeyPair.Public("a4991b82cb3f6b2818ce8fedc00ef919ba505bf9e67d96439b63937d24e4d19d509dd07ac95949e815b307769f4e4d6c3ed5d6bd4883af23cb679b251468a8bc"),
      KeyPair.Secret("1a3c21bb6e303a384154a56a882f5b760a2d166161f6ccff15fc70e147161788")
    )

    val genesis = GenesisBuilder()
      .withChainId(ChainId(10))
      .addAlloc(Address(alloc), N("1" + "0" * 27))
      .addAlloc(Address(miner0), N("1" + "0" * 27))
      .addMiner(Address(miner0))
//      .addMiner(Address(miner1))
//      .addMiner(Address(miner2))
      .build

    val config = CoreModule.testConfig.copy(genesis = genesis)

    val home = System.getProperty("user.home")
    val root = Paths.get(home).resolve(".iotchain")

    val builder = NetworkBuilder(config)
      .withBlockPeriod(10000)
      .addNode(miner0, coinbase0, root.resolve("node-0"), "127.0.0.2")
      .addNode(miner1, coinbase1, root.resolve("node-1"), "127.0.0.3")
      .addNode(miner2, coinbase2, root.resolve("node-2"), "127.0.0.4")

    builder.dump.as(ExitCode.Success)
  }
}
