package jbok.persistent

import cats.effect.IO
import jbok.JbokSpec
import scodec.bits.ByteVector
import jbok.codec.codecs._

class SnapshotKeyValueStoreFixture {
  val db = KeyValueDB.inMemory[IO].unsafeRunSync()
  val store = new KeyValueStore[IO, String, String](ByteVector.empty, db)
  val snapshot = SnapshotKeyValueStore(store)
}

class SnapshotKeyValueStoreSpec extends JbokSpec {
  "SnapshotKeyValueStore" should {
    "not write inserts until commit" in new SnapshotKeyValueStoreFixture {
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
