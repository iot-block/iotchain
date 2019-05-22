package jbok.core.api

import cats.effect.{ExitCode, IO, IOApp}
import jbok.common.log.{Level, Logger}

object JbokClientSpec extends IOApp {
  Logger.setRootHandlers[IO](Logger.consoleHandler(minimumLevel = Some(Level.Info))).unsafeRunSync()

  override def run(args: List[String]): IO[ExitCode] = {
    val url = "http://localhost:30315"
    JbokClientPlatform.resource[IO](url, None).use { client =>
      for {
        _ <- client.personal.newAccount("")
        accounts <- client.personal.listAccounts
      } yield ExitCode.Success
    }
  }
}
