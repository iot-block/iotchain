package jbok.core.config

import jbok.JbokSpec

class ConfigHelperSpec extends JbokSpec {
  "ConfigHelper" should {
    "print config help" in {
      val help = ConfigHelper.printConfig(ConfigHelper.reference)
      println(help.render)
    }

    "parse config" in {
      val args             = List("-datadir", "oho")
      val Right(cmdConfig) = ConfigHelper.parseConfig(args)
      val fullConfig       = cmdConfig.withFallback(ConfigHelper.reference)
      println(fullConfig.root().render())
      println(ConfigHelper.printConfig(fullConfig).render)
    }
  }
}
