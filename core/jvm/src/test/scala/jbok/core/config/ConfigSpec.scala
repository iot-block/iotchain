package jbok.core.config

import jbok.JbokSpec
import jbok.core.config.Configs.CoreConfig

class ConfigSpec extends JbokSpec {
  "Config" should {
    "load FullNodeConfig from resource" in {
      CoreConfig.fromJson(CoreConfig.reference.toJson) shouldBe CoreConfig.reference
    }
  }
}
