package jbok.crypto.authds.mpt

import cats.effect.IO
import jbok.JbokSpec
import jbok.persistent.{KeyValueDB, SnapshotKeyValueStore}
import scodec.bits.ByteVector
import jbok.codec.rlp.codecs._

class SnapshotMPTStoreSpecFixture {
  val db = KeyValueDB.inMemory[IO].unsafeRunSync()
  val mpt = MPTrie[IO](db).unsafeRunSync()
  val store = new MPTrieStore[IO, String, String](ByteVector.empty, mpt)
  val snapshot = SnapshotKeyValueStore(store)
}

class SnapshotMPTStoreSpec extends JbokSpec {
  "SnapshotMptStore" should {
    "not write inserts until commit" in new SnapshotMPTStoreSpecFixture {
      val updated = snapshot
        .put("1", "1")
        .put("2", "2")

      updated.has("1").unsafeRunSync() shouldBe true
      updated.has("2").unsafeRunSync() shouldBe true
      updated.inner.has("1").unsafeRunSync() shouldBe false
      updated.inner.has("2").unsafeRunSync() shouldBe false

      snapshot.has("1").unsafeRunSync() shouldBe false
      snapshot.has("2").unsafeRunSync() shouldBe false
      snapshot.inner.has("1").unsafeRunSync() shouldBe false
      snapshot.inner.has("2").unsafeRunSync() shouldBe false

      val committed = updated.commit().unsafeRunSync()
      committed.inner.has("1").unsafeRunSync() shouldBe true
      committed.inner.has("2").unsafeRunSync() shouldBe true

      // after commit
      snapshot.has("1").unsafeRunSync() shouldBe true
      snapshot.has("2").unsafeRunSync() shouldBe true
    }
  }
}
