package jbok.app

import _root_.io.circe.generic.auto._
import _root_.io.circe.syntax._
import better.files.File
import cats.effect.IO
import cats.implicits._
import jbok.app.TestnetBuilder.Topology
import jbok.core.config.Configs.{FullNodeConfig, PeerConfig}
import jbok.core.config.GenesisConfig
import jbok.core.consensus.poa.clique.Clique
import jbok.core.keystore.KeyStorePlatform
import jbok.core.models.Address
import jbok.core.peer.PeerNode
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}

@SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
class TestnetBuilder(
    n: Int = 0,
    configs: List[FullNodeConfig] = Nil,
    miners: List[Address] = Nil,
    keyPairs: List[KeyPair] = Nil,
    chainId: BigInt = 0,
    alloc: Map[Address, BigInt] = Map.empty
) {
  type Self = TestnetBuilder

  private def copy(
      n: Int = n,
      configs: List[FullNodeConfig] = configs,
      miners: List[Address] = miners,
      keyPairs: List[KeyPair] = keyPairs,
      chainId: BigInt = chainId,
      alloc: Map[Address, BigInt] = alloc
  ): Self = new TestnetBuilder(
    n,
    configs,
    miners,
    keyPairs,
    chainId,
    alloc
  )

  def nodeKeyPairs: List[KeyPair] =
    configs.map(_.nodeKeyPair)

  def check(): Unit = {
    require(n > 0)
    require(configs.length == n)
    require(miners.nonEmpty)
    require(keyPairs.length == n)
    require(nodeKeyPairs.length == n)
    require(chainId > 0)
    require(alloc.size == keyPairs.length)
  }

  def build: IO[List[FullNodeConfig]] = {
    check()
    (writeConf >> writeGenesis >> writeKeyStore).as(configs)
  }

  def withN(n: Int): Self = {
    val keyPairs     = (0 until n).toList.map(_ => Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync())
    val nodeKeyPairs = (0 until n).toList.map(_ => Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync())
    val configs = (0 until n).toList.map { i =>
      FullNodeConfig.reference
        .withIdentityAndPort(s"test-${i}", 20000 + (i * 3))
        .withMining(_.copy(minerAddress = Address(keyPairs(i))))
    }

    writeNodeKey(configs, nodeKeyPairs)

    copy(n = n, configs = configs, keyPairs = keyPairs)
  }

  def withTopology(topology: Topology): Self = {
    val xs = topology match {
      case Topology.Star =>
        configs.headOption
          .map { config =>
            val kp = config.nodeKeyPair
            val bootUris =
              List(PeerNode(kp.public, config.peer.host, config.peer.port, config.peer.discoveryPort).uri.toString)
            val tails = configs.tail.map(_.withPeer(_.copy(bootUris = bootUris)))
            config :: tails
          }
          .getOrElse(List.empty)

      case Topology.Ring =>
        (configs ++ configs.take(1)).sliding(2).toList.map {
          case a :: b :: Nil =>
            val kp = b.nodeKeyPair
            val bootUris =
              List(PeerNode(kp.public, b.peer.host, b.peer.port, b.peer.discoveryPort).uri.toString)
            a.withPeer(_.copy(bootUris = bootUris))
          case _ => ???
        }

      case Topology.Mesh =>
        val bootUris = configs.map { config =>
          val kp = config.nodeKeyPair
          PeerNode(kp.public, config.peer.host, config.peer.port, config.peer.discoveryPort).uri.toString
        }

        configs.map(_.withPeer(_.copy(bootUris = bootUris)))
    }

    copy(configs = xs)
  }

  def withChainId(bigInt: BigInt): Self =
    copy(chainId = bigInt)

  def withBalance(bigInt: BigInt): Self = {
    val alloc = keyPairs.map(kp => Address(kp) -> bigInt).toMap
    copy(alloc = alloc)
  }

  def withMiners(m: Int): Self = {
    require(m <= n)
    copy(
      configs = configs.take(m).map(_.withMining(_.copy(enabled = true))) ++ configs.drop(m),
      miners = keyPairs.take(m).map(kp => Address(kp)),
    )
  }

  private def writeKeyStore: IO[Unit] =
    configs
      .zip(keyPairs)
      .map {
        case (config, kp) =>
          KeyStorePlatform[IO](config.keystoreDir)
            .unsafeRunSync()
            .importPrivateKey(kp.secret.bytes, "")
      }
      .sequence_

  private def writeNodeKey(configs: List[FullNodeConfig], keyPairs: List[KeyPair]): IO[Unit] =
    configs
      .zip(keyPairs)
      .map {
        case (config, kp) =>
          PeerConfig.saveNodeKey(config.nodeKeyPath, kp)
      }
      .sequence_

  private def writeGenesis: IO[Unit] = {
    val genesisTemplate = GenesisConfig.generate(chainId, alloc)
    val genesis         = Clique.generateGenesisConfig(genesisTemplate, miners)
    configs.map { config =>
      IO(File(config.genesisPath).createIfNotExists(createParents = true).overwrite(genesis.asJson.spaces2))
    }.sequence_
  }

  private def writeConf: IO[Unit] =
    configs.map { config =>
      IO(File(s"${config.dataDir}/app.json").createIfNotExists(createParents = true).overwrite(config.toJson))
    }.sequence_
}

object TestnetBuilder {
  sealed trait Topology
  object Topology {
    case object Star extends Topology
    case object Ring extends Topology
    case object Mesh extends Topology
  }

  def apply(): TestnetBuilder = new TestnetBuilder()
}
