package jbok.persistent

import cats.effect.IO
import jbok.JbokSpec
import jbok.common.testkit._
import jbok.persistent.leveldb.LevelDB
import scodec.bits.ByteVector
import better.files._

class LevelDBSpec extends JbokSpec {
  "LevelDB" should {
    val bytesGen = genBoundedBytes(0, 1024).map(ByteVector.apply)

    "be able to insert and retrieve" in {
      val tmp = File.newTemporaryDirectory()
      println(tmp.pathAsString)
      val db = LevelDB[IO](tmp.pathAsString).unsafeRunSync()
      forAll(bytesGen, bytesGen) { (key, value) =>
        db.putRaw(key, value).unsafeRunSync()
        db.getRaw(key).unsafeRunSync() shouldBe Some(value)
      }
      tmp.delete()
    }

    "remove keys" in {
      val tmp = File.newTemporaryDirectory()
      println(tmp.pathAsString)
      val db = LevelDB[IO](tmp.pathAsString).unsafeRunSync()
      forAll(bytesGen, bytesGen) { (key, value) =>
        db.putRaw(key, value).unsafeRunSync()
        db.getRaw(key).unsafeRunSync() shouldBe Some(value)
        db.delRaw(key).unsafeRunSync()
        db.getRaw(key).unsafeRunSync() shouldBe None
      }
      tmp.delete()
    }
  }
}
