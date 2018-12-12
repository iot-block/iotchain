package jbok.core.config

import jbok.JbokSpec

class ConfigHelperSpec extends JbokSpec {
  "ConfigHelper" should {
    "print config help" in {
      val help = ConfigHelper.printConfig(ConfigHelper.reference)
      println(help.render)
    }

    "parse config" in {
      val args             = List("-identity", "my-node-2")
      val Right(cmdConfig) = ConfigHelper.parseConfig(args)

      val config = ConfigHelper.overrideWith(cmdConfig)
      println(ConfigHelper.printConfig(config).render)
    }
  }
}
