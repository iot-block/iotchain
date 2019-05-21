package jbok.app

import java.net.InetSocketAddress
import java.nio.file.Paths

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import io.circe.syntax._
import jbok.core.config.{CoreConfig, GenesisBuilder}
import jbok.common.config.Config
import jbok.core.models.Address
import jbok.core.peer.PeerUri
import jbok.crypto.signature.KeyPair.Secret
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import monocle.macros.syntax.lens._

import scala.concurrent.duration._

final case class NetworkBuilder(
    base: CoreConfig,
    configs: List[CoreConfig] = Nil,
) {
  def withBlockPeriod(n: Int): NetworkBuilder =
    copy(base = base.lens(_.mining.period).set(n.seconds))

  def withTrustStorePath(path: String): NetworkBuilder =
    copy(base = base.lens(_.ssl.trustStorePath).set(path))

  def addPeerNode(port: Int, host: String = "localhost"): NetworkBuilder = {
    val config =
      base
        .lens(_.peer.host).set(host)
        .lens(_.peer.port).set(port)
        .lens(_.service.host).set(host)
        .lens(_.service.port).set(port + 1)
    copy(configs = config :: configs)
  }

  def addMinerNode(secret: Secret, coinbase: Address, port: Int, host: String = "localhost", sslKeyStorePath: String = "/server.jks"): NetworkBuilder = {
    val config = base
      .lens(_.peer.host).set(host)
      .lens(_.peer.port).set(port)
      .lens(_.service.host).set(host)
      .lens(_.service.port).set(port + 1)
      .lens(_.mining.enabled).set(true)
      .lens(_.mining.secret).set(secret.bytes)
      .lens(_.mining.coinbase).set(coinbase)
      .lens(_.ssl.enabled).set(true)
      .lens(_.ssl.keyStorePath).set(sslKeyStorePath)

    copy(configs = config :: configs)
  }

  def build: List[CoreConfig] = {
    val reversed = configs.reverse
    val seeds = reversed.map(_.peer).map { peer =>
      PeerUri.fromTcpAddr(new InetSocketAddress(peer.host, peer.port)).uri
    }

    reversed.map(config => config.lens(_.peer.seeds).set(seeds))
  }

  def dump: IO[Unit] =
    build.zipWithIndex.traverse_ { case (config, i) => Config[IO].dump(config.asJson, Paths.get(s"config-${i}.yaml")) }
}

object NetworkBuilder extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    def randomKP: KeyPair =
      Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync()

    val miner1 = randomKP
    val miner2 = randomKP
    val miner3 = randomKP
    val miner4 = randomKP

    val coinbase1 = Address(randomKP)
    val coinbase2 = Address(randomKP)
    val coinbase3 = Address(randomKP)
    val coinbase4 = Address(randomKP)

    val genesis = GenesisBuilder()
      .withChainId(10)
      .addAlloc(Address(randomKP), BigInt("1" + "0" * 30))
      .addMiner(Address(miner1))
      .addMiner(Address(miner2))
      //        .withMiner(miner3)
      //        .withMiner(miner4)
      .build

    AppModule.resource[IO]().use { locator =>
      val config = locator
        .get[CoreConfig]
        .lens(_.genesis)
        .set(genesis)

      val builder = NetworkBuilder(config)
        .withBlockPeriod(15)
        .withTrustStorePath("/Users/xsy/dbj/jbok/bin/generated/ca/cacert.jks")
        .addMinerNode(miner1.secret, coinbase1, 20000, "127.0.0.2", "/Users/xsy/dbj/jbok/bin/generated/certs/certs1/server.jks")
        .addMinerNode(miner2.secret, coinbase2, 20000, "127.0.0.3", "/Users/xsy/dbj/jbok/bin/generated/certs/certs2/server.jks")
        .addPeerNode(20000, "127.0.0.4")
        .addPeerNode(20000, "127.0.0.5")

      builder.dump.as(ExitCode.Success)
    }
  }
}
