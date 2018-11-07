package jbok.crypto.authds.mpt

import cats.effect.IO
import jbok.JbokSpec
import jbok.persistent.{KeyValueDB, SnapshotKeyValueDB}
import scodec.bits.ByteVector
import jbok.codec.rlp.implicits._

class SnapshotMerkleTrieSpec extends JbokSpec {
  trait Fixture {
    val db        = KeyValueDB.inmem[IO].unsafeRunSync()
    val namespace = ByteVector.empty
    val mpt       = MerklePatriciaTrie[IO](namespace, db).unsafeRunSync()
    val snapshot  = SnapshotKeyValueDB[IO, String, String](namespace, mpt)
  }

  "SnapshotMerkleTrie" should {
    "not write inserts until commit" in new Fixture {
      val updated = snapshot
        .put("1", "1")
        .put("2", "2")

      updated.has("1").unsafeRunSync() shouldBe true
      updated.has("2").unsafeRunSync() shouldBe true
      updated.inner.has[String]("1", namespace).unsafeRunSync() shouldBe false
      updated.inner.has[String]("2", namespace).unsafeRunSync() shouldBe false

      snapshot.has("1").unsafeRunSync() shouldBe false
      snapshot.has("2").unsafeRunSync() shouldBe false
      snapshot.inner.has[String]("1", namespace).unsafeRunSync() shouldBe false
      snapshot.inner.has[String]("2", namespace).unsafeRunSync() shouldBe false

      val committed = updated.commit.unsafeRunSync()
      committed.inner.has[String]("1", namespace).unsafeRunSync() shouldBe true
      committed.inner.has[String]("2", namespace).unsafeRunSync() shouldBe true

      // after commit
      snapshot.has("1").unsafeRunSync() shouldBe true
      snapshot.has("2").unsafeRunSync() shouldBe true
    }
  }
}
