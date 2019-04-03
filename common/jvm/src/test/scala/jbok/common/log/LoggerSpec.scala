package jbok.common.log

import java.nio.file.Paths

import jbok.JbokSpec
import jbok.common.FileUtil

class LoggerSpec extends JbokSpec {
  "Logger" should {
    "set root log level" in {
      Log.i("should be seen").unsafeRunSync()
      Log.setRootLevel(Level.Error).unsafeRunSync()
      Log.i("should not be seen").unsafeRunSync()
    }

    "set file sink" in {
      Log.setRootHandlers(LogJVM.fileHandler("logs", Some(Level.Info))).unsafeRunSync()
      Log.i("should be written into file").unsafeRunSync()
      Log.t("should not be written into file").unsafeRunSync()

      val path = Paths.get("logs/jbok.log")
      val text = FileUtil.read(path).unsafeRunSync()
      text.contains("should be written into file") shouldBe true
      text.contains("should not be written into file") shouldBe false
      FileUtil.remove(path).unsafeRunSync()
    }

    "level1" ignore {
      Log.t("hello").unsafeRunSync()
      Log.w("hello").unsafeRunSync()
      Log.d("hello").unsafeRunSync()
      Log.i("hello").unsafeRunSync()
      Log.e("hello").unsafeRunSync()
    }

    "level2" ignore {
      val t = new Exception("oho")
      Log.t("hello", t).unsafeRunSync()
      Log.w("hello", t).unsafeRunSync()
      Log.d("hello", t).unsafeRunSync()
      Log.i("hello", t).unsafeRunSync()
      Log.e("hello", t).unsafeRunSync()
    }
  }
}
