package jbok.app

import java.net.InetSocketAddress
import java.nio.file.Paths

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import io.circe.syntax._
import jbok.app.config.FullConfig
import jbok.common.config.Config
import jbok.core.config.GenesisConfig
import jbok.core.models.Address
import jbok.core.peer.PeerUri
import jbok.crypto.signature.KeyPair.Secret
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import monocle.macros.syntax.lens._
import scodec.bits.ByteVector

import scala.concurrent.duration._

final case class NetworkBuilder(
    base: FullConfig,
    configs: List[FullConfig] = Nil,
) {
  def withBlockPeriod(n: Int): NetworkBuilder =
    copy(base = base.lens(_.core.mining.period).set(n.seconds))

  def withTrustStorePath(path: String): NetworkBuilder =
    copy(base = base.lens(_.core.ssl.trustStorePath).set(path))

  def addPeerNode(port: Int, host: String = "localhost"): NetworkBuilder = {
    val config =
      base
        .lens(_.core.peer.host).set(host)
        .lens(_.core.peer.port).set(port)
        .lens(_.app.service.host).set(host)
        .lens(_.app.service.port).set(port + 1)
    copy(configs = config :: configs)
  }

  def addMinerNode(secret: Secret, coinbase: Address, port: Int, host: String = "localhost", sslKeyStorePath: String = "/server.jks"): NetworkBuilder = {
    val config = base
      .lens(_.core.peer.host).set(host)
      .lens(_.core.peer.port).set(port)
      .lens(_.app.service.host).set(host)
      .lens(_.app.service.port).set(port + 1)
      .lens(_.core.mining.enabled).set(true)
      .lens(_.core.mining.secret).set(secret.bytes)
      .lens(_.core.mining.coinbase).set(coinbase)
      .lens(_.core.ssl.enabled).set(true)
      .lens(_.core.ssl.keyStorePath).set(sslKeyStorePath)

    copy(configs = config :: configs)
  }

  def build: List[FullConfig] = {
    val reversed = configs.reverse
    val seeds = reversed.map(_.core.peer).map { peer =>
      PeerUri.fromTcpAddr(new InetSocketAddress(peer.host, peer.port)).uri
    }

    reversed.map(config => config.lens(_.core.peer.seeds).set(seeds))
  }

  def dump: IO[Unit] =
    build.zipWithIndex.traverse_ { case (config, i) => Config[IO].dump(config.asJson, Paths.get(s"config-${i}.yaml")) }
}

object NetworkBuilder extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val base = GenesisConfig(
      difficulty = BigInt("1024"),
      extraData = ByteVector.empty,
      gasLimit = BigInt("16716680"),
      coinbase = ByteVector.empty,
      alloc = Map.empty,
      chainId = BigInt(0),
      timestamp = 0
    )

    def randomKP: KeyPair =
      Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync()

    val miner1 = randomKP.secret
    val miner2 = randomKP.secret
    val miner3 = randomKP.secret
    val miner4 = randomKP.secret

    val coinbase1 = Address(randomKP)
    val coinbase2 = Address(randomKP)
    val coinbase3 = Address(randomKP)
    val coinbase4 = Address(randomKP)

    val genesis = GenesisBuilder(base)
      .withAlloc(Address(randomKP), BigInt("1" + "0" * 30))
      .withChainId(10)
      .withMiner(miner1)
      .withMiner(miner2)
      //        .withMiner(miner3)
      //        .withMiner(miner4)
      .build

    AppModule.resource[IO]().use { locator =>
      val config = locator
        .get[FullConfig]
        .lens(_.core.genesis)
        .set(genesis)

      val builder = NetworkBuilder(config)
        .withBlockPeriod(15)
        .withTrustStorePath("/Users/xsy/dbj/jbok/bin/generated/ca/cacert.jks")
        .addMinerNode(miner1, coinbase1, 20000, "127.0.0.2", "/Users/xsy/dbj/jbok/bin/generated/certs/certs1/server.jks")
        .addMinerNode(miner2, coinbase2, 20000, "127.0.0.3", "/Users/xsy/dbj/jbok/bin/generated/certs/certs2/server.jks")
        .addPeerNode(20000, "127.0.0.4")
        .addPeerNode(20000, "127.0.0.5")

      builder.dump.as(ExitCode.Success)
    }
  }
}
