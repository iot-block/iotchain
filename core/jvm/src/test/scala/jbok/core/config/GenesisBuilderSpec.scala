package jbok.core.config

import jbok.common.CommonSpec
import jbok.common.testkit._
import jbok.core.models.Address
import jbok.core.testkit._

class GenesisBuilderSpec extends CommonSpec {
  "GenesisBuilder" should {
    "build genesis" in {
      val genesis = GenesisBuilder()
        .addAlloc(random[Address], BigInt("1" + "0" * 30))
        .withChainId(10)
        .addMiner(random[Address])
        .addMiner(random[Address])
        .addMiner(random[Address])
        .addMiner(random[Address])
        .build

      genesis.miners.length shouldBe 4
      genesis.chainId shouldBe BigInt(10)
      genesis.alloc.size shouldBe 1
    }
  }
}
