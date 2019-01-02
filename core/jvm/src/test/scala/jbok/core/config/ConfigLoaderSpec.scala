package jbok.core.config

import cats.effect.IO
import jbok.JbokSpec
import io.circe.syntax._
import io.circe.generic.auto._
import jbok.codec.json.implicits._

class ConfigLoaderSpec extends JbokSpec {
  "ConfigLoader" should {
    "load reference" in {
      val full = ConfigLoader.loadFullNodeConfig[IO](TypeSafeConfigHelper.reference).unsafeRunSync()
      println(full.asJson.spaces2)
    }
  }
}
