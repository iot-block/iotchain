package jbok.app

import java.net.InetSocketAddress

import _root_.io.circe.generic.auto._
import _root_.io.circe.syntax._
import better.files.File
import cats.effect.{ExitCode, IO}
import cats.implicits._
import com.typesafe.config.Config
import fs2._
import jbok.app.api.SimulationAPI
import jbok.app.simulations.SimulationImpl
import jbok.codec.rlp.implicits._
import jbok.common.metrics.Metrics
import jbok.core.config.Configs.FullNodeConfig
import jbok.core.config.{TypeSafeConfigHelper, ConfigLoader, GenesisConfig}
import jbok.core.consensus.poa.clique.Clique
import jbok.core.keystore.KeyStorePlatform
import jbok.network.rpc.RpcServer
import jbok.network.server.Server

import scala.concurrent.duration._

@SuppressWarnings(Array("org.wartremover.warts.OptionPartial", "org.wartremover.warts.EitherProjectionPartial"))
object MainApp extends StreamApp {
  val buildVersion = getClass.getPackage.getImplementationVersion

  val version = s"v${buildVersion} © 2018 The JBOK Authors"

  val banner = """
                  | ____     ____     ____     ____
                  |||J ||   ||B ||   ||O ||   ||K ||
                  |||__||<--||__||<--||__||<--||__||
                  ||/__\|   |/__\|   |/__\|   |/__\|
                  |""".stripMargin

  def parseConfig(args: List[String]): IO[Config] =
    for {
      cmdConfig <- IO(TypeSafeConfigHelper.parseCmdArgs(args).right.get)
      config = TypeSafeConfigHelper.overrideBy(cmdConfig)
    } yield config

  def loadConfig(config: Config): IO[FullNodeConfig] =
    for {
      fullNodeConfig <- ConfigLoader.loadFullNodeConfig[IO](config)
      _              <- IO(println(version))
      _              <- IO(println(banner))
      _              <- IO(println(TypeSafeConfigHelper.printConfig(config).render))
    } yield fullNodeConfig

  override def run(args: List[String]): IO[ExitCode] =
    args match {
      case "node" :: tail =>
        runStream {
          for {
            config         <- Stream.eval(parseConfig(tail))
            fullNodeConfig <- Stream.eval(loadConfig(config))
            fullNode       <- FullNode.stream(fullNodeConfig)
            _              <- fullNode.stream
          } yield ()
        }

      case "test-node" :: tail =>
        runStream {
          val testArgs = List(
            "-logLevel",
            "DEBUG",
            "-mining.enabled",
            "true",
            "-rpc.enabled",
            "true",
            "-history.chainDataDir",
            "inmem"
          )
          for {
            config         <- Stream.eval(parseConfig(testArgs ++ tail))
            fullNodeConfig <- Stream.eval(loadConfig(config))
            fullNode       <- FullNode.stream(fullNodeConfig)
            _              <- fullNode.stream
          } yield ()
        }

      case "genesis" :: tail =>
        for {
          config         <- parseConfig(tail)
          fullNodeConfig <- loadConfig(config)
          keystore       <- KeyStorePlatform[IO](fullNodeConfig.keystore.keystoreDir)
          address <- keystore.listAccounts.flatMap(
            _.headOption.fold(keystore.readPassphrase("please input your passphrase>") >>= keystore.newAccount)(IO.pure)
          )
          _ <- keystore.readPassphrase(s"unlock address ${address}>").flatMap(p => keystore.unlockAccount(address, p))
          signers = List(address)
          genesis = Clique.generateGenesisConfig(GenesisConfig.generate(0, Map.empty), signers)
          _ <- IO(File(fullNodeConfig.genesisPath).createIfNotExists().overwrite(genesis.asJson.spaces2))
        } yield ExitCode.Success

      case "simulation" :: tail =>
        val bind       = new InetSocketAddress("localhost", 8888)
        val peerCount  = 4
        val minerCount = 1
        for {
          impl      <- SimulationImpl()
          rpcServer <- RpcServer().map(_.mountAPI[SimulationAPI](impl))
          metrics   <- Metrics.default[IO]
          server = Server.websocket(bind, rpcServer.pipe, metrics)
          _ <- impl.createNodesWithMiner(peerCount, minerCount)
          _ = timer.sleep(5000.millis)
          ec <- runStream(server.stream)
        } yield ec

      case _ =>
        for {
          _ <- IO(println(version))
          _ <- IO(println(banner))
          _ <- IO(println(TypeSafeConfigHelper.printConfig(TypeSafeConfigHelper.reference).render))
        } yield ExitCode.Error
    }
}
