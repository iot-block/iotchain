package jbok.app
import java.nio.file.{Path, StandardOpenOption}

import better.files.File
import cats.effect.{ExitCode, IO, IOApp}
import jbok.core.config.{ConfigHelper, ConfigLoader}

object MainApp extends IOApp {
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

  override def run(args: List[String]): IO[ExitCode] =
    args match {
      case "node" :: tail =>
        for {
          cmdConfig <- IO(ConfigHelper.parseConfig(tail).right.get)
          config = cmdConfig.withFallback(ConfigHelper.reference)
          fullNodeConfig <- ConfigLoader.loadFullNodeConfig[IO](config)
          _              <- IO(println(version))
          _              <- IO(println(banner))
          _              <- IO(println(ConfigHelper.printConfig(config).render))
          fullNode       <- FullNode.forConfig(fullNodeConfig)
          _              <- fullNode.start
          _              <- IO.never
        } yield ExitCode.Success

      case _ =>
        for {
          _ <- IO(println(version))
          _ <- IO(println(banner))
          _ <- IO(println(ConfigHelper.printConfig(ConfigHelper.reference).render))
        } yield ExitCode.Error
    }
}
