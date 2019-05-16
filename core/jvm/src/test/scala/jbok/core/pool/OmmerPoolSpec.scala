package jbok.core.pool

import cats.effect.IO
import jbok.common.CommonSpec
import jbok.core.models.Block
import jbok.common.testkit._
import jbok.core.CoreSpec
import jbok.core.testkit._
import monocle.macros.syntax.lens._

class OmmerPoolSpec extends CoreSpec {
  "OmmerPool" should {
    "accept ommers" in {
      val n       = 10
      val pool    = locator.unsafeRunSync().get[OmmerPool[IO]]
      val blocks  = random[List[Block]](genBlocks(n, n))
      val headers = blocks.map(_.header)
      pool.addOmmers(headers).unsafeRunSync()
      val ommers = pool.getOmmers(n + 1).unsafeRunSync()
      ommers shouldBe headers.takeRight(config.ommerPool.ommerGenerationLimit).take(config.ommerPool.ommerSizeLimit)
    }

    "remove ommers" in {
      val n       = 6
      val pool    = locator.unsafeRunSync().get[OmmerPool[IO]]
      val blocks  = random[List[Block]](genBlocks(n, n))
      val headers = blocks.map(_.header)
      pool.addOmmers(headers).unsafeRunSync()
      pool.removeOmmers(headers.take(2)).unsafeRunSync()
      pool.getOmmers(4).unsafeRunSync() shouldBe headers.slice(2, 3)
    }

    "return ommers when out of pool size" in {
      val config2 = config.lens(_.ommerPool.poolSize).set(3)
      val pool    = withConfig(config2).unsafeRunSync().get[OmmerPool[IO]]
      val blocks  = random[List[Block]](genBlocks(5, 5))
      val headers = blocks.map(_.header)

      pool.addOmmers(headers.take(2)).unsafeRunSync()
      pool.addOmmers(headers.takeRight(2)).unsafeRunSync()
      val ommers = pool.getOmmers(3).unsafeRunSync()
      ommers.length shouldBe 1
    }
  }
}
