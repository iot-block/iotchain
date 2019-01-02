package jbok.core.config

import cats.effect.IO
import jbok.JbokSpec

class TypeSafeConfigHelperSpec extends JbokSpec {
  "TypeSafeConfigHelper" should {
    "print config help" in {
      val help = TypeSafeConfigHelper.printConfig(TypeSafeConfigHelper.reference)
      println(help.render)
    }

    "parse and override config" in {
      val identity = "custom-node"
      val port     = 10000

      val config           = TypeSafeConfigHelper.withIdentityAndPort(identity, port)
      val jbok             = ConfigLoader.loadFullNodeConfig[IO](config).unsafeRunSync()

      jbok.identity shouldBe identity
      jbok.dataDir shouldBe s"${jbok.rootDir}/${identity}"
      jbok.history.chainDataDir shouldBe s"${jbok.dataDir}/chainData"
      jbok.peer.peerDataDir shouldBe s"${jbok.dataDir}/peerData"
      jbok.keystore.keystoreDir shouldBe s"${jbok.dataDir}/keystore"

      jbok.peer.port shouldBe port
      jbok.peer.discoveryPort shouldBe port + 1
      jbok.rpc.port shouldBe port + 2
    }
  }
}
