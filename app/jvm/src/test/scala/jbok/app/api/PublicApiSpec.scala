package jbok.app.api

import cats.effect.IO
import cats.implicits._
import jbok.JbokSpec
import jbok.app.api.impl.PublicApiImpl
import jbok.common.testkit.random
import jbok.core.mining.BlockMiner
import jbok.common.testkit._
import jbok.core.testkit._

class PublicApiSpec extends JbokSpec {
  implicit val config = testConfig
  val miner           = random[BlockMiner[IO]]
  val publicApi       = PublicApiImpl(config.history, miner)
  "public api" should {
    "return gasPrice when block number < 30" in {
      miner.stream.take(15).compile.toList.unsafeRunSync()
      publicApi.getGasPrice.unsafeRunSync() shouldBe BigInt(0)
    }

    "return gasPrice when block number >= 30" in {
      miner.stream.take(30).compile.toList.unsafeRunSync()
      publicApi.getGasPrice.unsafeRunSync() shouldBe BigInt(0)
    }
  }
}
