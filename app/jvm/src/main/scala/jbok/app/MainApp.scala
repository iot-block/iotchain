package jbok.app

import cats.effect.{ExitCode, IO}
import ch.qos.logback.classic.Level
import com.typesafe.config.Config
import fs2._
import jbok.common.logger
import jbok.core.config.Configs.FullNodeConfig
import jbok.core.config.{ConfigHelper, ConfigLoader}

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
            fullNode       <- Stream.resource(FullNode.forConfig(fullNodeConfig))
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
            fullNode       <- Stream.resource(FullNode.forConfig(fullNodeConfig))
            _              <- fullNode.stream
          } yield ()
        }

      case _ =>
        for {
          _ <- IO(println(version))
          _ <- IO(println(banner))
          _ <- IO(println(ConfigHelper.printConfig(ConfigHelper.reference).render))
        } yield ExitCode.Error
    }
}
