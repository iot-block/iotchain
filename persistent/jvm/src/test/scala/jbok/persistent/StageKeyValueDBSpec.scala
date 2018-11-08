package jbok.persistent

import cats.effect.IO
import jbok.JbokSpec
import jbok.codec.rlp.implicits._
import scodec.bits.ByteVector

class StageKeyValueDBSpec extends JbokSpec {
  class Fixture {
    val db        = KeyValueDB.inmem[IO].unsafeRunSync()
    val namespace = ByteVector.empty
    val stage  = StageKeyValueDB[IO, String, String](namespace, db)
  }

  "StageKeyValueDB" should {
    "not write inserts until commit" in new Fixture {
      val updated = stage
        .put("1", "1")
        .put("2", "2")

      updated.has("1").unsafeRunSync() shouldBe true
      updated.has("2").unsafeRunSync() shouldBe true
      updated.inner.has[String]("1", namespace).unsafeRunSync() shouldBe false
      updated.inner.has[String]("2", namespace).unsafeRunSync() shouldBe false

      stage.has("1").unsafeRunSync() shouldBe false
      stage.has("2").unsafeRunSync() shouldBe false
      stage.inner.has[String]("1", namespace).unsafeRunSync() shouldBe false
      stage.inner.has[String]("2", namespace).unsafeRunSync() shouldBe false

      val committed = updated.commit.unsafeRunSync()
      committed.inner.has[String]("1", namespace).unsafeRunSync() shouldBe true
      committed.inner.has[String]("2", namespace).unsafeRunSync() shouldBe true

      // after commit
      stage.has("1").unsafeRunSync() shouldBe true
      stage.has("2").unsafeRunSync() shouldBe true
    }
  }
}
