package jbok.persistent

import cats.effect.IO
import jbok.JbokSpec
import jbok.common.testkit.ByteGen
import scodec.bits.ByteVector

class KeyValueDBSpec extends JbokSpec {
  def check(db: KeyValueDB[IO]): Unit =
    s"${db.getClass.getSimpleName}" should {
      val bytesGen = ByteGen.genBoundedBytes(0, 1024).map(ByteVector.apply)

      "be able to insert and retrieve" in {
        forAll(bytesGen, bytesGen) { (key, value) =>
          db.put(key, value).unsafeRunSync()
          db.get(key).unsafeRunSync() shouldBe value
          db.getOpt(key).unsafeRunSync() shouldBe Some(value)
        }

        db.clear().unsafeRunSync()
      }

      "remove keys" in {
        forAll(bytesGen, bytesGen) { (key, value) =>
          db.put(key, value).unsafeRunSync()
          db.get(key).unsafeRunSync() shouldBe value
          db.del(key).unsafeRunSync()
          db.getOpt(key).unsafeRunSync() shouldBe None
        }

        db.clear().unsafeRunSync()
      }

      "remove all keys after clear" in {
        val kvs =
          (1 to 10).map(_ => bytesGen.sample.get -> bytesGen.sample.get)

        kvs.foreach {
          case (k, v) =>
            db.put(k, v).unsafeRunSync()
        }

        db.keys.unsafeRunSync().isEmpty shouldBe false
        db.clear().unsafeRunSync()

        kvs.foreach {
          case (key, value) =>
            db.getOpt(key).unsafeRunSync() shouldBe None
        }
        db.keys.unsafeRunSync().isEmpty shouldBe true
      }
    }
}
