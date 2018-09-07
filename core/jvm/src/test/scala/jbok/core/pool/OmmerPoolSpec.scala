package jbok.core.pool

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.Fixtures

trait OmmerPoolFixture extends BlockPoolFixture {
  val ommerPool = OmmerPool[IO](history, 3).unsafeRunSync()
}

class OmmerPoolSpec extends JbokSpec {
  import Fixtures.Blocks.Block3125369

  "OmmerPool" should {
    "accept ommers" in new OmmerPoolFixture {
      ommerPool.addOmmers(List(Block3125369.header)).unsafeRunSync()
      ommerPool.getOmmers(Block3125369.header.number + 1).unsafeRunSync() shouldBe List(Block3125369.header)
    }

    "remove ommers" in new OmmerPoolFixture {
      ommerPool.addOmmers(List(Block3125369.header)).unsafeRunSync()
      ommerPool.addOmmers(List(Block3125369.header.copy(number = 2))).unsafeRunSync()
      ommerPool.removeOmmers(List(Block3125369.header)).unsafeRunSync()
      ommerPool.getOmmers(3).unsafeRunSync() shouldBe List(Block3125369.header.copy(number = 2))
    }

    "return ommers when out of pool size" in new OmmerPoolFixture {
      ommerPool.addOmmers(List(Block3125369.header.copy(number = 4))).unsafeRunSync()
      ommerPool.addOmmers(List(Block3125369.header.copy(number = 20))).unsafeRunSync()
      ommerPool.addOmmers(List(Block3125369.header.copy(number = 30))).unsafeRunSync()
      ommerPool.addOmmers(List(Block3125369.header.copy(number = 40))).unsafeRunSync()
      ommerPool.addOmmers(List(Block3125369.header.copy(number = 5))).unsafeRunSync()
      ommerPool.getOmmers(6).unsafeRunSync() shouldBe List(Block3125369.header.copy(number = 5))
    }
  }
}
