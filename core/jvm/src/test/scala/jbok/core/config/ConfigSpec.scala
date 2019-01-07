package jbok.core.config

import jbok.JbokSpec
import jbok.core.config.Configs.FullNodeConfig

class ConfigSpec extends JbokSpec {
  "Config" should {
    "load FullNodeConfig from resource" in {
      FullNodeConfig.fromJson(FullNodeConfig.reference.toJson) shouldBe FullNodeConfig.reference
    }
  }
}
