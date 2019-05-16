package jbok.core.config

import io.circe.syntax._
import jbok.common.testkit._
import jbok.core.CoreSpec
import jbok.core.models.Address
import jbok.core.testkit._

class GenesisBuilderSpec extends CoreSpec {
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

      println(genesis.asJson.spaces2)
    }
  }
}
