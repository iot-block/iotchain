package jbok.crypto.authds.mpt

import cats.effect.IO
import jbok.common.CommonSpec
import jbok.persistent.{KeyValueDB, StageKeyValueDB}
import scodec.bits.ByteVector
import jbok.codec.rlp.implicits._

class StageMPTSpec extends CommonSpec {
  trait Fixture {
    val db        = KeyValueDB.inmem[IO].unsafeRunSync()
    val namespace = ByteVector.empty
    val mpt       = MerklePatriciaTrie[IO](namespace, db).unsafeRunSync()
    val stage     = StageKeyValueDB[IO, String, String](namespace, mpt)
  }

  "Staged MPT" should {
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
