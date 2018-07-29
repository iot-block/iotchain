package jbok.core

import com.typesafe.config.ConfigFactory
import jbok.JbokSpec
import jbok.core.configs._

class ConfigSpec extends JbokSpec {
  val config = ConfigFactory.load()

  "configs" should {
    "load configs" in {
      loadConfig[configs.NetworkConfig](config, "jbok.network").isRight shouldBe true
      loadConfig[configs.KeyStoreConfig](config, "jbok.keystore").isRight shouldBe true
      loadConfig[configs.PeerManagerConfig](config, "jbok.peer").isRight shouldBe true
      loadConfig[configs.SyncConfig](config, "jbok.sync").isRight shouldBe true
      loadConfig[configs.BlockChainConfig](config, "jbok.blockchain").isRight shouldBe true
    }
  }
}
