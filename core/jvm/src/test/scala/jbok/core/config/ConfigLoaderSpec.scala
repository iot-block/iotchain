package jbok.core.config

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.config.Configs._
import pureconfig.generic.auto._
import io.circe.syntax._
import io.circe.generic.auto._
import jbok.codec.json.implicits._
import jbok.core.config.ConfigLoader._

class ConfigLoaderSpec extends JbokSpec {
  "ConfigLoader" should {
    val config = ConfigHelper.reference

    "load full" in {
      val full = ConfigLoader.loadFullNodeConfig[IO](config).unsafeRunSync()
      println(full.asJson.spaces2)
    }
  }
}
