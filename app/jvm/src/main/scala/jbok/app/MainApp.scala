package jbok.app

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import fs2._
import jbok.core.config.Configs.FullNodeConfig
import jbok.core.config.{ConfigHelper, ConfigLoader}

object MainApp extends StreamApp {
  val buildVersion = getClass.getPackage.getImplementationVersion

  val version = s"v${buildVersion} © 2018 The JBOK Authors"

  val banner = """
           | .----------------.  .----------------.  .----------------.  .----------------.
           || .--------------. || .--------------. || .--------------. || .--------------. |
           || |     _____    | || |   ______     | || |     ____     | || |  ___  ____   | |
           || |    |_   _|   | || |  |_   _ \    | || |   .'    `.   | || | |_  ||_  _|  | |
           || |      | |     | || |    | |_) |   | || |  /  .--.  \  | || |   | |_/ /    | |
           || |   _  | |     | || |    |  __'.   | || |  | |    | |  | || |   |  __'.    | |
           || |  | |_' |     | || |   _| |__) |  | || |  \  `--'  /  | || |  _| |  \ \_  | |
           || |  `.___.'     | || |  |_______/   | || |   `.____.'   | || | |____||____| | |
           || |              | || |              | || |              | || |              | |
           || '--------------' || '--------------' || '--------------' || '--------------' |
           | '----------------'  '----------------'  '----------------'  '----------------'
           |
           |""".stripMargin

  def loadConfig(args: List[String]): IO[FullNodeConfig] =
    for {
      cmdConfig <- IO(ConfigHelper.parseConfig(args).right.get)
      config = cmdConfig.withFallback(ConfigHelper.reference)
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
            config   <- Stream.eval(loadConfig(tail))
            fullNode <- Stream.resource(FullNode.forConfig(config))
            _        <- fullNode.stream
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
