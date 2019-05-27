package jbok.core.config

import java.net.InetSocketAddress
import java.nio.file.{Path, Paths}

import cats.effect.IO
import cats.implicits._
import io.circe.syntax._
import jbok.common.config.Config
import jbok.core.keystore.KeyStorePlatform
import jbok.core.models.Address
import jbok.core.peer.PeerUri
import jbok.crypto.signature.KeyPair
import monocle.macros.syntax.lens._

import scala.concurrent.duration._

final case class NetworkBuilder(
    base: FullConfig,
    configs: List[FullConfig] = Nil,
) {
  def withBlockPeriod(n: Int): NetworkBuilder =
    copy(base = base.lens(_.mining.period).set(n.millis))

  def withTrustStorePath(path: String): NetworkBuilder =
    copy(base = base.lens(_.ssl.trustStorePath).set(path))

  def addNode(keyPair: KeyPair, coinbase: Address, rootPath: Path, host: String, sslKeyStorePath: Path): NetworkBuilder = {
    val config = base
      .lens(_.rootPath).set(rootPath.toAbsolutePath.toString)
      .lens(_.peer.host).set(host)
      .lens(_.service.host).set(host)
      .lens(_.service.secure).set(true)
      .lens(_.mining.enabled).set(true)
      .lens(_.mining.address).set(Address(keyPair))
      .lens(_.mining.coinbase).set(coinbase)
      .lens(_.ssl.enabled).set(true)
      .lens(_.ssl.keyStorePath).set(sslKeyStorePath.toAbsolutePath.toString)
      .lens(_.persist.driver).set("rocksdb")
      .lens(_.persist.path).set(s"${rootPath.resolve("data").toAbsolutePath}")
      .lens(_.log.logDir).set(s"${rootPath.resolve("logs").toAbsolutePath}")
      .lens(_.keystore.dir).set(s"${rootPath.resolve("keystore").toAbsolutePath}")
      .lens(_.db.driver).set("org.sqlite.JDBC")
      .lens(_.db.url).set(s"jdbc:sqlite:${rootPath.resolve(s"service.db")}")

    val keystore = new KeyStorePlatform[IO](config.keystore)
    keystore.importPrivateKey(keyPair.secret.bytes, "changeit").unsafeRunSync()

    copy(configs = config :: configs)
  }

  def build: List[FullConfig] = {
    val reversed = configs.reverse
    val seeds = reversed.map(_.peer).map { peer =>
      PeerUri.fromTcpAddr(new InetSocketAddress(peer.host, peer.port)).uri
    }

    reversed.zipWithIndex.map { case (config, i) => config.lens(_.peer.seeds).set(seeds.take(i) ++ seeds.drop(i + 1)) }
  }

  def dump: IO[Unit] =
    build.traverse_(config => Config[IO].dump(config.asJson, Paths.get(config.rootPath).resolve(s"config.yaml")))
}
