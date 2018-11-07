package jbok.core.config
import jbok.JbokSpec
import io.circe.syntax._
import io.circe.parser._
import jbok.codec.json.implicits._

class GenesisConfigSpec extends JbokSpec {
  "GenesisConfig" should {
    "roundtrip to json" in {
      val json = GenesisConfig.default.asJson.spaces2
      decode[GenesisConfig](json).right.get shouldBe GenesisConfig.default
    }
  }
}
