package jbok.app

import java.security.SecureRandom

import _root_.io.circe.generic.auto._
import _root_.io.circe.syntax._
import better.files.File
import cats.effect.{ExitCode, IO}
import cats.implicits._
import com.typesafe.config.Config
import fs2._
import jbok.common.logger
import jbok.core.config.Configs.FullNodeConfig
import jbok.core.config.{ConfigHelper, ConfigLoader}
import jbok.core.consensus.poa.clique
import jbok.core.keystore.KeyStorePlatform

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
      cmdConfig <- IO(ConfigHelper.parseConfig(args).right.get)
      config = ConfigHelper.overrideWith(cmdConfig)
    } yield config

  def loadConfig(config: Config): IO[FullNodeConfig] =
    for {
      fullNodeConfig <- ConfigLoader.loadFullNodeConfig[IO](config)
      _              <- logger.setRootLevel[IO](fullNodeConfig.logLevel)
      _              <- IO(println(version))
      _              <- IO(println(banner))
      _              <- IO(println(ConfigHelper.printConfig(config).render))
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
            _.headOption.fold(keystore.readPassphrase("please input your passphrase:") >>= keystore.newAccount)(IO.pure)
          )
          _ <- keystore.readPassphrase(s"unlock address ${address}:").flatMap(p => keystore.unlockAccount(address, p))
          signers = List(address)
          genesis = clique.generateGenesisConfig(fullNodeConfig.genesis, signers)
          _ <- IO(File(fullNodeConfig.genesisPath).createIfNotExists().overwrite(genesis.asJson.spaces2))
        } yield ExitCode.Success

      case _ =>
        for {
          _ <- IO(println(version))
          _ <- IO(println(banner))
          _ <- IO(println(ConfigHelper.printConfig(ConfigHelper.reference).render))
        } yield ExitCode.Error
    }
}
