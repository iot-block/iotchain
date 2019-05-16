package jbok.core.config

import cats.effect.IO
import io.circe.syntax._
import jbok.common.{CommonSpec, FileUtil}
import jbok.common.config.Config

class ConfigSpec extends CommonSpec {
  "Config" should {
    "load and dump" in {
      val config = Config[IO].readResource[CoreConfig]("config.test.yaml").unsafeRunSync()

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
