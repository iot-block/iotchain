package jbok.app

import java.net.InetSocketAddress
import java.nio.file.{Path, Paths}

import _root_.io.circe.generic.auto._
import _root_.io.circe.syntax._
import better.files.File
import cats.effect.{ExitCode, IO}
import cats.implicits._
import fs2._
import jbok.app.NetworkBuilder.Topology
import jbok.app.api.{SimulationAPI, TestNetTxGen}
import jbok.app.config.{PeerNodeConfig, ServiceConfig}
import jbok.app.simulations.SimulationImpl
import jbok.codec.rlp.implicits._
import jbok.common.metrics.Metrics
import jbok.core.config.Configs.CoreConfig
import jbok.core.config.GenesisConfig
import jbok.core.consensus.poa.clique.Clique
import jbok.core.keystore.KeyStorePlatform
import jbok.core.models.Address
import jbok.network.rpc.RpcService
import jbok.network.server.{Server, WsServer}
import scodec.bits.ByteVector
import jbok.codec.json.implicits._
import jbok.common.config.Config

import scala.collection.immutable.ListMap
import scala.concurrent.duration._

@SuppressWarnings(Array("org.wartremover.warts.OptionPartial", "org.wartremover.warts.EitherProjectionPartial"))
object MainApp extends StreamApp {
  private val buildVersion: String = getClass.getPackage.getImplementationVersion

  private val version = s"v${buildVersion} © 2018 - 2019 The JBOK Authors"

  private val banner: String = """
                  | ____     ____     ____     ____
                  |||J ||   ||B ||   ||O ||   ||K ||
                  |||__||<--||__||<--||__||<--||__||
                  ||/__\|   |/__\|   |/__\|   |/__\|
                  |""".stripMargin

  private def loadConfig(path: Path): IO[PeerNodeConfig] =
    for {
      _      <- IO(println(version))
      _      <- IO(println(banner))
      config <- Config.read[PeerNodeConfig](path)
    } yield config

  override def run(args: List[String]): IO[ExitCode] =
    args match {
      // run with default config
      case "node" :: Nil =>
        ???
//        runStream {
//          for {
//            fullNode <- FullNode.stream(PeerNodeConfig(CoreConfig.reference, ServiceConfig.config))
//            _        <- fullNode.stream
//          } yield ()
//        }

      // read config fro path
      case "node" :: path :: Nil =>
        runStream {
          for {
            config   <- Stream.eval(loadConfig(Paths.get(path)))
            fullNode <- FullNode.stream(config)
            _        <- fullNode.stream
          } yield ()
        }

      // generate genesis config
      case "genesis" :: Nil =>
        for {
          keystore <- KeyStorePlatform[IO](CoreConfig.reference.keystoreDir)
          address <- keystore.listAccounts.flatMap(
            _.headOption.fold(keystore.readPassphrase("please input your passphrase>") >>= keystore.newAccount)(IO.pure)
          )
          _ <- keystore.readPassphrase(s"unlock address ${address}>").flatMap(p => keystore.unlockAccount(address, p))
          signers = List(address)
          genesis = Clique.generateGenesisConfig(GenesisConfig.generate(0, ListMap.empty), signers)
          _ <- IO(File(CoreConfig.reference.genesisPath).createIfNotExists().overwrite(genesis.asJson.spaces2))
        } yield ExitCode.Success

      case "simulation" :: tail =>
        val bind = new InetSocketAddress("localhost", 8888)
        for {
          impl    <- SimulationImpl()
          metrics <- Metrics.default[IO]
          service = RpcService().mountAPI[SimulationAPI](impl)
          server  = WsServer.bind(bind, service.pipe, metrics)
          _       = timer.sleep(5000.millis)
          ec <- runStream(server.stream)
        } yield ec

      case "txgen" :: tail =>
        for {
          nTx  <- tail.headOption.flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(2).pure[IO]
          txtg <- TestNetTxGen(nTx)
          ec   <- runStream(txtg.run)
        } yield ExitCode.Success

      // generate configs
      case "network-builder" :: tail =>
        for {
          addresses <- tail
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

      case _ =>
        for {
          _ <- IO(println(version))
          _ <- IO(println(banner))
        } yield ExitCode.Error
    }
}
