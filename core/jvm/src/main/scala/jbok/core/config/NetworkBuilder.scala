package jbok.core.config

import java.nio.file.Paths

import cats.effect.IO
import cats.implicits._
import io.circe.syntax._
import jbok.common.config.Config
import jbok.core.config.NetworkBuilder.Topology
import jbok.core.keystore.KeyStorePlatform
import jbok.core.models.Address
import jbok.core.peer.PeerUri
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import monocle.macros.syntax.lens._

import scala.collection.immutable.ListMap
import scala.concurrent.duration._

final case class NetworkBuilder(
    base: CoreConfig,
    configs: List[CoreConfig] = Nil,
    miners: List[Address] = Nil,
    keyPairs: List[KeyPair] = Nil,
    alloc: ListMap[Address, BigInt] = ListMap.empty
) {
  type Self = NetworkBuilder
  val homePath   = System.getProperty("user.home")
  val passphrase = "changeit"

  def getRootPath(i: Int) = Paths.get(s"${homePath}/.jbok/node-${i}")

  def withN(n: Int): Self = {
    require(n > 0)
    val keyPairs = (0 until n).toList.map(_ => Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync())
    val configs = (0 until n).toList.map { i =>
      val rootPath = getRootPath(i)
      base
        .lens(_.peer.port)
        .set(20000 + (i * 2))
        .lens(_.service.port)
        .set(20001 + (i * 2))
        .lens(_.persist.path)
        .set(s"${rootPath.resolve("data").toAbsolutePath}")
        .lens(_.log.logDir)
        .set(s"${rootPath.resolve("logs").toAbsolutePath}")
        .lens(_.keystore.dir)
        .set(s"${rootPath.resolve("keystore").toAbsolutePath}")
        .lens(_.persist.driver)
        .set("rocksdb")
        .lens(_.ssl.enabled)
        .set(false)
        .lens(_.db.driver)
        .set("org.sqlite.JDBC")
        .lens(_.db.url)
        .set(s"jdbc:sqlite:${rootPath.resolve("service.db").toAbsolutePath}")
    }

    copy(configs = configs, keyPairs = keyPairs)
  }

  def withAlloc(addresses: List[Address] = List.empty, bigInt: BigInt = BigInt("1" + "0" * 30)): Self = {
    val alloc = ListMap((keyPairs.map(kp => Address(kp)) ++ addresses).map(_ -> bigInt): _*)
    copy(alloc = alloc)
  }

  def withTopology(topology: Topology): Self = {
    val xs = topology match {
      case Topology.Star =>
        configs.headOption
          .map { config =>
            val bootUris = List(PeerUri.fromTcpAddr(config.peer.bindAddr).uri)
            val tails    = configs.tail.map(_.lens(_.peer.seeds).set(bootUris))
            config :: tails
          }
          .getOrElse(List.empty)

      case Topology.Ring =>
        (configs ++ configs.take(1)).sliding(2).toList.map {
          case a :: b :: Nil =>
            val bootUris = List(PeerUri.fromTcpAddr(b.peer.bindAddr).uri)
            a.lens(_.peer.seeds).set(bootUris)
          case _ => ???
        }

      case Topology.Mesh =>
        val bootUris = configs.map { config =>
          PeerUri.fromTcpAddr(config.peer.bindAddr).uri
        }

        configs.map(_.lens(_.peer.seeds).set(bootUris))
    }

    copy(configs = xs)
  }

  def withBlockPeriod(n: Int): NetworkBuilder =
    copy(base = base.lens(_.mining.period).set(n.millis))

  def withMiners(m: Int): Self = {
    require(m <= configs.length)
    copy(
      configs = configs.take(m).zip(keyPairs).map {
        case (config, keyPair) => config.lens(_.mining.enabled).set(true).lens(_.mining.passphrase).set(passphrase).lens(_.mining.coinbase).set(Address(keyPair))
      } ++ configs.drop(m),
      miners = keyPairs.take(m).map(kp => Address(kp))
    )
  }

  def build: Self = {
    check()
    copy(configs = applyGenesis)
  }

  def check(): Unit = require(miners.nonEmpty)

  private def applyGenesis: List[CoreConfig] =
    configs.map(
      config =>
        config
          .lens(_.genesis.alloc)
          .set(alloc.toMap)
          .lens(_.genesis.miners)
          .set(miners))

  private def writeConf: IO[Unit] =
    configs.zipWithIndex.traverse_ {
      case (config, i) =>
        Config[IO].dump(config.asJson, getRootPath(i).resolve(s"config.yaml"))
    }

  private def writeKeyStore: IO[Unit] =
    configs
      .zip(keyPairs)
      .map {
        case (config, kp) =>
          KeyStorePlatform.resource[IO](config.keystore).use { keyStore =>
            keyStore.importPrivateKey(kp.secret.bytes, passphrase)
          }
      }
      .sequence_

  def dump: IO[Unit] = {
    val builder = build
    builder.writeConf >> builder.writeKeyStore
  }
}

object NetworkBuilder {
  sealed trait Topology
  object Topology {
    case object Star extends Topology
    case object Ring extends Topology
    case object Mesh extends Topology
  }
}
