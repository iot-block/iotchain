package jbok.app

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import distage.Injector

object MainApp extends IOApp {
  private val buildVersion: String = getClass.getPackage.getImplementationVersion

  private val version = s"v${buildVersion} © 2018 - 2019 The JBOK Authors"

  private val banner: String = """
                                 | ____     ____     ____     ____
                                 |||J ||   ||B ||   ||O ||   ||K ||
                                 |||__||<--||__||<--||__||<--||__||
                                 ||/__\|   |/__\|   |/__\|   |/__\|
                                 |""".stripMargin

  override def run(args: List[String]): IO[ExitCode] =
    Injector()
      .produceF[IO](new AppModule[IO])
      .use(_.get[FullNode[IO]].stream.compile.drain)
      .as(ExitCode.Success)
}
