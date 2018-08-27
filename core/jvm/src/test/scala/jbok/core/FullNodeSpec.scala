package jbok.core

import cats.effect.IO
import cats.implicits._
import jbok.JbokSpec
import jbok.core.configs.FullNodeConfig
import jbok.network.execution._

class FullNodeSpec extends JbokSpec {
  "FullNode" should {
    "create a full node" in {
      val fullNodeConfig = FullNodeConfig("1", 10001)
      val fullNode       = FullNode.inMemory[IO](fullNodeConfig).unsafeRunSync()
      fullNode.start.unsafeRunSync()
      fullNode.stop.unsafeRunSync()
    }

    "create a bunch of nodes" in {
      val configs = FullNodeConfig.fill(10)
      val nodes   = configs.map(config => FullNode.inMemory[IO](config).unsafeRunSync())

      nodes.traverse(_.start).unsafeRunSync()
      nodes.traverse(_.stop).unsafeRunSync()
    }
  }
}
