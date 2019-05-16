package jbok.common.log

import java.nio.file.Paths

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
      Logger.setRootHandlers(LoggerPlatform.fileHandler("logs", Some(Level.Info))).unsafeRunSync()
      Logger[IO].i("should be written into file").unsafeRunSync()
      Logger[IO].t("should not be written into file").unsafeRunSync()

      val path = Paths.get("logs/jbok.log")
      val text = FileUtil[IO].read(path).unsafeRunSync()
      text.contains("should be written into file") shouldBe true
      text.contains("should not be written into file") shouldBe false
      FileUtil[IO].remove(path).unsafeRunSync()
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
