package jbok.core.config

import com.typesafe.config.ConfigFactory
import jbok.JbokSpec

class ConfigHelperSpec extends JbokSpec {
  val config = ConfigFactory.load().getConfig("jbok")

  "ConfigHelper" should {
    "print config help" in {
      val help = ConfigHelper.printConfig(config)
      println(help.render)
    }

    "parse config" in {
      val args             = List("-datadir", "oho")
      val Right(cmdConfig) = ConfigHelper.parseConfig(args)
      val fullConfig       = cmdConfig.withFallback(config)
      println(ConfigHelper.printConfig(fullConfig).render)
    }
  }
}
