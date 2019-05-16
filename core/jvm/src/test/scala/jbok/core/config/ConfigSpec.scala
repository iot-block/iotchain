package jbok.core.config

import java.nio.charset.StandardCharsets

import better.files.Resource
import cats.effect.IO
import io.circe.syntax._
import jbok.common.CommonSpec
import jbok.common.FileUtil
import jbok.common.config.Config
import jbok.core.config.Configs.CoreConfig

class ConfigSpec extends CommonSpec {
  "Config" should {
    "load and dump" in {
      val text   = Resource.getAsString("config.test.yaml")(StandardCharsets.UTF_8)
      val config = Config[IO].read[CoreConfig](text).unsafeRunSync()

      FileUtil[IO].temporaryFile(suffix = ".yaml")
        .use { file =>
          for {
            _       <- Config[IO].dump(config.asJson, file.path)
            config2 <- Config[IO].read[CoreConfig](file.path)
            _ = config2 shouldBe config
          } yield ()
        }
        .unsafeRunSync()
    }
  }
}
