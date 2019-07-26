package jbok.app


import cats.implicits._
import cats.effect._
import jbok.core.config.KeyStoreConfig
import jbok.core.keystore.{KeyStorePlatform}

object ToolMain extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    new KeyStorePlatform[IO](KeyStoreConfig("", "keystore"))
    .newAccount(args.headOption.getOrElse("changeit")).as(ExitCode.Success)

}
