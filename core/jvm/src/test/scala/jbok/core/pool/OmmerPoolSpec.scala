package jbok.core.pool

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.models.Block
import jbok.common.testkit._
import jbok.core.testkit._

class OmmerPoolSpec extends JbokSpec {
  implicit val fixture = defaultFixture()

  "OmmerPool" should {
    "accept ommers" in {
      val n       = 10
      val pool    = random[OmmerPool[IO]]
      val blocks  = random[List[Block]](genBlocks(n, n))
      val headers = blocks.map(_.header)
      pool.addOmmers(headers).unsafeRunSync()
      val ommers = pool.getOmmers(n + 1).unsafeRunSync()
      ommers shouldBe headers.takeRight(OmmerPool.OmmerGenerationLimit).take(OmmerPool.OmmerSizeLimit)
    }

    "remove ommers" in {
      val n       = 6
      val pool    = random[OmmerPool[IO]]
      val blocks  = random[List[Block]](genBlocks(n, n))
      val headers = blocks.map(_.header)
      pool.addOmmers(headers).unsafeRunSync()
      pool.removeOmmers(headers.take(2)).unsafeRunSync()
      pool.getOmmers(4).unsafeRunSync() shouldBe headers.slice(2, 3)
    }

    "return ommers when out of pool size" in {
      val pool    = random[OmmerPool[IO]](genOmmerPool(poolSize = 3))
      val blocks  = random[List[Block]](genBlocks(5, 5))
      val headers = blocks.map(_.header)

      pool.addOmmers(headers.take(2)).unsafeRunSync()
      pool.addOmmers(headers.takeRight(2)).unsafeRunSync()
      val ommers = pool.getOmmers(3).unsafeRunSync()
      ommers.length shouldBe 1
    }
  }
}
