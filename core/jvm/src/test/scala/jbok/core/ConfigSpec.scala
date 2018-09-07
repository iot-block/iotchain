package jbok.core

import jbok.JbokSpec
import jbok.core.Configs._

class ConfigSpec extends JbokSpec {
  "configs" should {
    "load configs" in {
      loadConfig[NetworkConfig]("jbok.network").isRight shouldBe true
      loadConfig[KeyStoreConfig]("jbok.keystore").isRight shouldBe true
      loadConfig[PeerManagerConfig]("jbok.peer").isRight shouldBe true
      loadConfig[SyncConfig]("jbok.sync").isRight shouldBe true
      loadConfig[BlockChainConfig]("jbok.blockchain").isRight shouldBe true
    }
  }
}
