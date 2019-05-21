package jbok.app

import java.nio.file.Paths

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import jbok.common.log.Logger

object AppMain extends IOApp {
  private[this] val log = Logger[IO]

  private val buildVersion: String = getClass.getPackage.getImplementationVersion

  private val banner: String = """
                                 | ____     ____     ____     ____
                                 |||J ||   ||B ||   ||O ||   ||K ||
                                 |||__||<--||__||<--||__||<--||__||
                                 ||/__\|   |/__\|   |/__\|   |/__\|
                                 |""".stripMargin

  private val version = s"v${buildVersion} © 2018 - 2019 The JBOK Authors"

  override def run(args: List[String]): IO[ExitCode] =
    log.i(banner) >>
      log.i(version) >>
      AppModule
        .resource[IO](Paths.get(args.headOption.getOrElse("/etc/jbok/config.yaml")))
        .use(_.get[FullNode[IO]].stream.compile.drain)
        .as(ExitCode.Success)
}
