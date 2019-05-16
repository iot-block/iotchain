package jbok.app
import jbok.common.CommonSpec
import jbok.app.NetworkBuilder.Topology
import jbok.core.models.Address

class NetworkBuilderSpec extends CommonSpec {
  "NetworkBuilder" should {
    "dry run" in {
      val address =
        Address.fromHex("0xa9f26854C08E6A707c9378839C24B5c085F8cE11")

      val builder = NetworkBuilder()
        .withNumOfNodes(4)
        .withMiners(2)
        .withAlloc(List(address), BigInt("1" + "0" * 28))
        .withChainId(1)
        .withTopology(Topology.Star)

      builder.dryRun.unsafeRunSync()
    }
  }
}
