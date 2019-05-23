package jbok.common.log

import cats.effect.IO
import jbok.common.CommonSpec
import jbok.common.FileUtil
import scala.concurrent.duration._

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
          _ <- Logger.setRootLevel[IO](Level.Info)
          _ <- Logger.setRootHandlers[IO](Logger.consoleHandler(Some(Level.Info)), LoggerPlatform.fileHandler(file.path, Some(Level.Info)))
          _ <- Logger[IO].i("should be written into file")
          _ <- Logger[IO].t("should not be written into file")
          _ = println(file.list.toList)
          text <- FileUtil[IO].read(file.path.resolve("jbok.log"))
          _ = text.contains("should be written into file") shouldBe true
          _ = text.contains("should not be written into file") shouldBe false
        } yield ()
      }

      p.unsafeRunSync()
    }

    "level1" ignore {
      Logger[IO].t("hello").unsafeRunSync()
      Logger[IO].w("hello").unsafeRunSync()
      Logger[IO].d("hello").unsafeRunSync()
      Logger[IO].i("hello").unsafeRunSync()
      Logger[IO].e("hello").unsafeRunSync()
    }

    "level2" ignore {
      val t = new Exception("oho")
      Logger[IO].t("hello", t).unsafeRunSync()
      Logger[IO].w("hello", t).unsafeRunSync()
      Logger[IO].d("hello", t).unsafeRunSync()
      Logger[IO].i("hello", t).unsafeRunSync()
      Logger[IO].e("hello", t).unsafeRunSync()
    }
  }
}
