package jbok.app

import java.nio.file.{Path, Paths}

import _root_.io.circe.generic.auto._
import _root_.io.circe.syntax._
import cats.effect.IO
import cats.implicits._
import io.circe.{Encoder, Json}
import jbok.app.NetworkBuilder.Topology
import jbok.app.config.{LogConfig, PeerNodeConfig, ServiceConfig}
import jbok.core.config.Configs.{CoreConfig, PeerConfig}
import jbok.core.config.GenesisConfig
import jbok.core.consensus.poa.clique.Clique
import jbok.core.keystore.KeyStorePlatform
import jbok.core.models.Address
import jbok.core.peer.PeerNode
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import scodec.bits.ByteVector
import jbok.codec.json.implicits._
import jbok.common.FileUtil
import jbok.common.config.Config

import scala.collection.immutable.ListMap

sealed trait Action
object Action {
  final case class WriteJson(json: Json, path: Path)                     extends Action
  final case class ImportSecret(secret: ByteVector, keystoreDir: String) extends Action
  final case class SaveText(text: String, path: Path)                    extends Action
}

final case class NetworkBuilder(
    numOfNodes: Int = 0,
    configs: List[PeerNodeConfig] = Nil,
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
        Config.dump(json, path)

      case Action.ImportSecret(secret, dir) =>
        KeyStorePlatform[IO](dir).flatMap(_.importPrivateKey(secret, "").void)

      case Action.SaveText(text, path) =>
        FileUtil.dump(text, path)
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

  def withNumOfNodes(n: Int): NetworkBuilder = {
    val keyPairs     = (0 until n).toList.map(_ => Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync())
    val nodeKeyPairs = (0 until n).toList.map(_ => Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync())
    val configs = (0 until n).toList.map { i =>
      val port = 20000 + (i * 4)
      val core = CoreConfig.reference
        .withIdentityAndPort(s"test-${i}", 20000 + (i * 4))
        .withMining(_.copy(minerAddress = Address(keyPairs(i))))
      val log = LogConfig("DEBUG", "console,file")
      val service =
        ServiceConfig(
          enabled = true,
          "localhost",
          port + 3,
          s"jdbc:sqlite:${core.dataDir}/serviceData/jbok.sqlite",
          "JBOK",
          1000
        )
      PeerNodeConfig(s"test-${i}", core, log, service)
    }
    copy(numOfNodes = numOfNodes, configs = configs, keyPairs = keyPairs, nodeKeyPairs = nodeKeyPairs)
  }

  def withTopology(topology: Topology): NetworkBuilder = {
    val xs: List[PeerNodeConfig] = topology match {
      case Topology.Star =>
        configs
          .zip(nodeKeyPairs)
          .take(1)
          .flatMap {
            case (config, nodeKeyPair) =>
              val kp = nodeKeyPair
              val bootUris =
                List(
                  PeerNode(kp.public, config.core.peer.host, config.core.peer.port, config.core.peer.discoveryPort).uri.toString)
              val tails = configs.tail.map(c => c.copy(core = c.core.withPeer(_.copy(bootUris = bootUris))))
              config :: tails
          }

      case Topology.Ring =>
        val xs = configs.zip(nodeKeyPairs)
        (xs ++ xs.take(1))
          .sliding(2)
          .toList
          .map {
            case a :: b :: Nil =>
              val kp = b._2
              val bootUris =
                List(
                  PeerNode(kp.public, b._1.core.peer.host, b._1.core.peer.port, b._1.core.peer.discoveryPort).uri.toString)
              (a._1.copy(core = a._1.core.withPeer(_.copy(bootUris = bootUris))), a._2)
            case _ => ???
          }
          .map(_._1)

      case Topology.Mesh =>
        val bootUris = configs.zip(nodeKeyPairs).map {
          case (config, kp) =>
            PeerNode(kp.public, config.core.peer.host, config.core.peer.port, config.core.peer.discoveryPort).uri.toString
        }

        configs.map(c => c.copy(core = c.core.withPeer(_.copy(bootUris = bootUris))))
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
        .map(c => c.copy(core = c.core.withMining(_.copy(enabled = true)), service = c.service.copy(enabled = false))) ++ configs
        .drop(m),
      miners = keyPairs.take(m).map(kp => Address(kp)),
    )
}

object NetworkBuilder {
  sealed trait Topology
  object Topology {
    case object Star extends Topology
    case object Ring extends Topology
    case object Mesh extends Topology
  }
}
