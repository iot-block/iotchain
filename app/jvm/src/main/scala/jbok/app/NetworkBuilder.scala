package jbok.app

import java.nio.file.{Path, Paths}

import _root_.io.circe.generic.auto._
import jbok.codec.json.implicits._
import _root_.io.circe.syntax._
import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import io.circe.Json
import jbok.app.NetworkBuilder.Topology
import jbok.app.config.AppConfig
import jbok.common.FileUtil
import jbok.common.config.Config
import jbok.core.config.GenesisConfig
import jbok.core.consensus.poa.clique.Clique
import jbok.core.keystore.KeyStorePlatform
import jbok.core.models.Address
import jbok.core.peer.PeerUri
import jbok.crypto.signature.KeyPair
import monocle.macros.syntax.lens._
import scodec.bits.ByteVector

import scala.collection.immutable.ListMap

sealed trait Action
object Action {
  final case class WriteJson(json: Json, path: Path)                     extends Action
  final case class ImportSecret(secret: ByteVector, keystoreDir: String) extends Action
  final case class SaveText(text: String, path: Path)                    extends Action
}

final case class NetworkBuilder(
    numOfNodes: Int = 0,
    configs: List[AppConfig] = Nil,
    miners: List[Address] = Nil,
    keyPairs: List[KeyPair] = Nil,
    nodeKeyPairs: List[KeyPair] = Nil,
    chainId: BigInt = 0,
    alloc: ListMap[Address, BigInt] = ListMap.empty
) {
  def check(): Unit = {
    require(numOfNodes > 0)
    require(configs.length == numOfNodes)
    require(miners.nonEmpty)
    require(keyPairs.length == numOfNodes)
    require(nodeKeyPairs.length == numOfNodes)
    require(chainId > 0)
  }

  def dryRun: IO[Unit] =
    printActions(actions)

  def build: IO[Unit] =
    actions.traverse_ {
      case Action.WriteJson(json, path) =>
        Config[IO].dump(json, path)

      case Action.ImportSecret(secret, dir) =>
        KeyStorePlatform[IO](dir).flatMap(_.importPrivateKey(secret, "").void)

      case Action.SaveText(text, path) =>
        FileUtil[IO].dump(text, path)
    }

  def printActions(actions: List[Action]): IO[Unit] = IO {
    println(actions.mkString("\n"))
  }

  def actions: List[Action] = {
    val writeConfigsActions: List[Action] =
      configs.map(config => Action.WriteJson(config.asJson, Paths.get(s"${config.core.dataDir}/app.json")))

    val writeGenesisActions: List[Action] = {
      val genesisTemplate = GenesisConfig.generate(chainId, alloc)
      val genesis         = Clique.generateGenesisConfig(genesisTemplate, miners)
      configs.map(config => Action.WriteJson(genesis.asJson, Paths.get(s"${config.core.genesisPath}")))
    }

    val writeKeystoreActions: List[Action] =
      configs
        .zip(keyPairs)
        .map { case (config, kp) => Action.ImportSecret(kp.secret.bytes, config.core.keystoreDir) }

    val writeNodeKeys: List[Action] =
      configs.zip(keyPairs).map { case (config, kp) => Action.SaveText(kp.secret.bytes.toHex, Paths.get(config.core.nodeKeyPath)) }

    writeConfigsActions ++ writeGenesisActions ++ writeKeystoreActions ++ writeNodeKeys
  }

  def withNumOfNodes(n: Int): NetworkBuilder =
    ???
//    val keyPairs     = (0 until n).toList.map(_ => Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync())
//    val nodeKeyPairs = (0 until n).toList.map(_ => Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync())
//    val configs = (0 until n).toList.map { i =>
//      val port = 20000 + (i * 4)
//      val core = CoreConfig.reference
//        .withIdentityAndPort(s"test-${i}", 20000 + (i * 4))
//        .withMining(_.copy(minerAddress = Address(keyPairs(i))))
//      val service =
//        ServiceConfig(
//          enabled = true,
//          "localhost",
//          port + 3,
//          s"jdbc:sqlite:${core.dataDir}/serviceData/jbok.sqlite",
//          "JBOK",
//          1000
//        )
//      PeerNodeConfig(s"test-${i}", core, service)
//    }
//    copy(numOfNodes = numOfNodes, configs = configs, keyPairs = keyPairs, nodeKeyPairs = nodeKeyPairs)

  def withTopology(topology: Topology): NetworkBuilder = {
    val xs: List[AppConfig] = topology match {
      case Topology.Star =>
        configs
          .zip(nodeKeyPairs)
          .take(1)
          .flatMap {
            case (config, nodeKeyPair) =>
              val kp    = nodeKeyPair
              val seeds = List(PeerUri.fromTcpAddr(kp.public, config.core.peer.bindAddr).uri.toString)
              val tails = configs.tail.map(_.lens(_.core.peer.seeds).set(seeds))
              config :: tails
          }

      case Topology.Ring =>
        val xs = configs.zip(nodeKeyPairs)
        (xs ++ xs.take(1))
          .sliding(2)
          .toList
          .map {
            case a :: b :: Nil =>
              val kp    = b._2
              val seeds = List(PeerUri.fromTcpAddr(kp.public, b._1.core.peer.bindAddr).uri.toString)
              (a._1.lens(_.core.peer.seeds).set(seeds), a._2)
            case _ => ???
          }
          .map(_._1)

      case Topology.Mesh =>
        val seeds = configs.zip(nodeKeyPairs).map {
          case (config, kp) => PeerUri.fromTcpAddr(kp.public, config.core.peer.bindAddr).uri.toString
        }
        configs.map(_.lens(_.core.peer.seeds).set(seeds))
    }

    copy(configs = xs)
  }

  def withChainId(bigInt: BigInt): NetworkBuilder =
    copy(chainId = bigInt)

  def withAlloc(addresses: List[Address], bigInt: BigInt): NetworkBuilder =
    copy(alloc = ListMap((keyPairs.map(kp => Address(kp)) ++ addresses).map(_ -> bigInt): _*))

  def withMiners(m: Int): NetworkBuilder =
    copy(
      configs = configs
        .take(m)
        .map(_.lens(_.core.mining.enabled).set(true).lens(_.service.enabled).set(true)) ++ configs
        .drop(m),
      miners = keyPairs.take(m).map(kp => Address(kp)),
    )
}

object NetworkBuilder extends IOApp {
  sealed trait Topology
  object Topology {
    case object Star extends Topology
    case object Ring extends Topology
    case object Mesh extends Topology
  }

  override def run(args: List[String]): IO[ExitCode] =
    for {
      addresses <- args.tail
        .map(hex => ByteVector.fromHex(hex.toLowerCase))
        .filter(_.exists(_.length <= 20))
        .traverse[Option, Address](_.map(Address.apply))
        .getOrElse(List.empty)
        .distinct
        .pure[IO]
      _ = println(addresses)
      _ <- NetworkBuilder()
        .withNumOfNodes(4)
        .withMiners(1)
        .withAlloc(addresses, BigInt("1" + "0" * 28))
        .withChainId(1)
        .withTopology(Topology.Star)
        .build
    } yield ExitCode.Success
}
