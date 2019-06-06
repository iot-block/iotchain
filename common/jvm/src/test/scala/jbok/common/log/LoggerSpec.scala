package jbok.common.log

import cats.effect.IO
import jbok.common.CommonSpec
import jbok.common.FileUtil

class LoggerSpec extends CommonSpec {
  "Logger" should {
    "set root log level" in {
      Logger[IO].i("should be seen").unsafeRunSync()
      Logger.setRootLevel(Level.Error).unsafeRunSync()
      Logger[IO].i("should not be seen").unsafeRunSync()
    }

    "set file sink" in {
      val p = FileUtil[IO].temporaryDir().use { file =>
        for {
          _    <- Logger.setRootLevel[IO](Level.Info)
          _    <- Logger.setRootHandlers[IO](Logger.consoleHandler(Some(Level.Info)), LoggerPlatform.fileHandler(file.path, Some(Level.Info)))
          _    <- Logger[IO].i("should be written into file")
          _    <- Logger[IO].t("should not be written into file")
          text <- FileUtil[IO].read(file.path.resolve("jbok.log"))
          _ = text.contains("should be written into file") shouldBe true
          _ = text.contains("should not be written into file") shouldBe false
        } yield ()
      }

      p.unsafeRunSync()
    }
  }
}
