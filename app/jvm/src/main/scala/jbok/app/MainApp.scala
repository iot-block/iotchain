package jbok.app

import java.security.SecureRandom

import _root_.io.circe.generic.auto._
import _root_.io.circe.syntax._
import better.files.File
import cats.effect.{ExitCode, IO}
import com.typesafe.config.Config
import fs2._
import jbok.common.logger
import jbok.core.config.Configs.FullNodeConfig
import jbok.core.config.{ConfigHelper, ConfigLoader}
import jbok.core.consensus.poa.clique.Clique
import jbok.core.keystore.KeyStorePlatform
import jbok.core.models.Address

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
          keystore       <- KeyStorePlatform[IO](fullNodeConfig.keystore.keystoreDir, new SecureRandom())
          address <- keystore.listAccounts.flatMap {
            case Nil =>
              for {
                _          <- IO(println("please input your passphrase:"))
                passphrase <- IO((new scala.tools.jline_embedded.console.ConsoleReader).readLine(Character.valueOf(0)))
//                passphrase <- IO {System.console().readPassword().toString}
                address <- keystore.newAccount(passphrase)
              } yield address
            case head :: _ => IO.pure(head)
          }
          miners: List[Address] = List(address)
          genesis               = fullNodeConfig.genesis.copy(extraData = Clique.fillExtraData(miners))
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
