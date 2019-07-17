package jbok.app

import java.nio.file.Paths

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import jbok.common.log.Logger

object AppMain extends IOApp {
  private[this] val log = Logger[IO]

  private val buildVersion: String = getClass.getPackage.getImplementationVersion

  private val banner: String = """
                         | _____   _______    _____ _           _
                         ||_   _| |__   __|  / ____| |         (_)
                         |  | |  ___ | |    | |    | |__   __ _ _ _ __
                         |  | | / _ \| |    | |    | '_ \ / _` | | '_ \
                         | _| || (_) | |    | |____| | | | (_| | | | | |
                         ||_____\___/|_|     \_____|_| |_|\__,_|_|_| |_|
                         |""".stripMargin

  private val version = s"v${buildVersion} © 2018 - 2019 The IoTChain Authors"

  override def run(args: List[String]): IO[ExitCode] =
    log.i(banner) >>
      log.i(version) >>
      AppModule
        .resource[IO](Paths.get(args.headOption.getOrElse("/etc/iotchain/config.yaml")))
        .use(_.get[FullNode[IO]].stream.compile.drain)
        .as(ExitCode.Success)
}
