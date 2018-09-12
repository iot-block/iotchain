package jbok.persistent

import cats.effect.IO
import jbok.JbokSpec
import scodec.bits.ByteVector

class RefCountKeyValueDBSpec extends JbokSpec {
  val db = KeyValueDB.inMemory[IO].unsafeRunSync()

  "RefCountKeyValueDB" should {
    "not remove a key if no more reference until pruning" in {
      val rc = RefCountKeyValueDB.forVersion(db, 1)
      val inserted =
        (1 to 4).map(i => ByteVector.fromValidHex(s"dead$i") -> ByteVector.fromValidHex(s"beef${i}"))
      inserted.foreach(t => rc.put(t._1, t._2).unsafeRunSync())
      val (key1, value1) = inserted.head

      rc.del(key1).unsafeRunSync()
      rc.getOpt(key1).unsafeRunSync() shouldBe Some(value1)

      rc.prune(1).unsafeRunSync()
      rc.getOpt(key1).unsafeRunSync() shouldBe None
    }

    "not remove a key that was inserted after deletion when pruning" in {
      val rc = RefCountKeyValueDB.forVersion(db, 1)
      val inserted =
        (1 to 1).map(i => ByteVector.fromValidHex(s"dead$i") -> ByteVector.fromValidHex(s"beef${i}"))
      inserted.foreach(t => rc.put(t._1, t._2).unsafeRunSync())
      val (key1, value1) = inserted.head

      val rc2 = RefCountKeyValueDB.forVersion(db, 2)
      rc2.del(key1).unsafeRunSync()
      rc2.get(key1).unsafeRunSync() shouldBe value1

      val rc3 = RefCountKeyValueDB.forVersion(db, 3)
      rc3.put(key1, value1).unsafeRunSync()
      rc3.get(key1).unsafeRunSync() shouldBe value1

      val rc4 = RefCountKeyValueDB.forVersion(db, 4)
      rc4.del(key1).unsafeRunSync()
      rc3.get(key1).unsafeRunSync() shouldBe value1

      rc3.prune(1).unsafeRunSync()
      rc3.get(key1).unsafeRunSync() shouldBe value1

      rc3.prune(2).unsafeRunSync()
      rc3.get(key1).unsafeRunSync() shouldBe value1

      rc3.prune(3).unsafeRunSync()
      rc3.get(key1).unsafeRunSync() shouldBe value1

      rc3.prune(4).unsafeRunSync()
      rc3.getOpt(key1).unsafeRunSync() shouldBe None
    }

    "not remove a key that it's still referenced when pruning" in {
      val rc = RefCountKeyValueDB.forVersion(db, 1)
      val inserted =
        (1 to 1).map(i => ByteVector.fromValidHex(s"dead$i") -> ByteVector.fromValidHex(s"beef${i}"))
      inserted.foreach(t => rc.put(t._1, t._2).unsafeRunSync())
      val (key1, value1) = inserted.head

      val rc2 = RefCountKeyValueDB.forVersion(db, 2)
      rc2.put(key1, value1).unsafeRunSync()
      rc2.get(key1).unsafeRunSync() shouldBe value1

      val rc3 = RefCountKeyValueDB.forVersion(db, 3)
      rc3.del(key1).unsafeRunSync()
      rc3.get(key1).unsafeRunSync() shouldBe value1

      rc3.prune(1).unsafeRunSync()
      rc3.get(key1).unsafeRunSync() shouldEqual value1

      rc3.prune(2).unsafeRunSync()
      rc3.get(key1).unsafeRunSync() shouldEqual value1

      rc3.prune(3).unsafeRunSync()
      rc3.get(key1).unsafeRunSync() shouldEqual value1
    }

    "not delete a key that's was referenced in later blocks when pruning" in {
      val rc = RefCountKeyValueDB.forVersion(db, 1)
      val inserted =
        (1 to 4).map(i => ByteVector.fromValidHex(s"dead$i") -> ByteVector.fromValidHex(s"beef${i}")).toList
      inserted.foreach(t => rc.put(t._1, t._2).unsafeRunSync())
      val List((key1, val1), (key2, val2), (key3, val3), (key4, val4)) = inserted
      rc.del(key1).unsafeRunSync() // remove key1 at block 1
      rc.del(key4).unsafeRunSync() // remove key4 at block 1, it should be pruned

      val rc2 = RefCountKeyValueDB.forVersion(db, 2)

      rc2.put(key1, val1).unsafeRunSync()
      rc2.del(key1).unsafeRunSync() // add key1 again and remove it at block 2
      rc2.del(key2).unsafeRunSync()
      rc2.put(key2, val2).unsafeRunSync() // remove and add key2 at block 2
      rc2.del(key3).unsafeRunSync()       // Remove at block 2

      rc2.get(key1).unsafeRunSync() shouldEqual val1
      rc2.get(key2).unsafeRunSync() shouldEqual val2
      rc2.get(key3).unsafeRunSync() shouldEqual val3

      rc2.prune(1).unsafeRunSync()
      rc2.get(key1).unsafeRunSync() shouldEqual val1
      rc2.get(key2).unsafeRunSync() shouldEqual val2
      rc2.get(key3).unsafeRunSync() shouldEqual val3
      rc2.getOpt(key4).unsafeRunSync() shouldBe None

      rc2.prune(2).unsafeRunSync()
      rc2.getOpt(key1).unsafeRunSync() shouldBe None
      rc2.getOpt(key2).unsafeRunSync() shouldBe Some(val2)
      rc2.getOpt(key3).unsafeRunSync() shouldBe None
      rc2.getOpt(key4).unsafeRunSync() shouldBe None
    }

    "not throw an error when deleting a key that does not exist" in {
      val rc = RefCountKeyValueDB.forVersion(db, 1)
      rc.del(ByteVector("doesnotexit".getBytes)).unsafeRunSync()
      db.size.unsafeRunSync() shouldBe 0
    }

    "allow to rollback operations" in {
      val rc = RefCountKeyValueDB.forVersion(db, 1)
      val inserted =
        (1 to 4).map(i => ByteVector.fromValidHex(s"dead$i") -> ByteVector.fromValidHex(s"beef${i}")).toList
      inserted.foreach(t => rc.put(t._1, t._2).unsafeRunSync())
      val (key1, val1) :: (key2, val2) :: xs = inserted

      rc.del(key1).unsafeRunSync()
      rc.del(key2).unsafeRunSync()

      val rc2  = RefCountKeyValueDB.forVersion(db, 2)
      val key3 = ByteVector("anotherKey".getBytes)
      val val3 = ByteVector("anotherValue".getBytes)
      rc2.put(key3, val3).unsafeRunSync()

      rc2.get(key3).unsafeRunSync() shouldEqual val3

      rc2.rollback(2).unsafeRunSync()

      rc2.get(key1).unsafeRunSync() shouldEqual val1
      rc2.get(key2).unsafeRunSync() shouldEqual val2
      rc2.getOpt(key3).unsafeRunSync() shouldEqual None
    }

    "allow rollbacks after pruning" in {
      val rc = RefCountKeyValueDB.forVersion(db, 1)
      val inserted =
        (1 to 4).map(i => ByteVector.fromValidHex(s"dead$i") -> ByteVector.fromValidHex(s"beef${i}")).toList
      inserted.foreach(t => rc.put(t._1, t._2).unsafeRunSync())
      val (key1, val1) :: (key2, val2) :: xs = inserted

      rc.del(key1).unsafeRunSync()
      rc.del(key2).unsafeRunSync()

      val rc2  = RefCountKeyValueDB.forVersion(db, 2)
      val key3 = ByteVector("anotherKey".getBytes)
      val val3 = ByteVector("anotherValue".getBytes)
      rc2.put(key3, val3).unsafeRunSync()

      db.size.unsafeRunSync() shouldBe (5 + 2 + 7) // 5 keys + 2 block count + 7 snapshots

      rc2.prune(1).unsafeRunSync()
      db.size.unsafeRunSync() shouldEqual (3 + 1 + 1) // 3 keys + 1 block index + 1 snapshots

      // Data is correct
      rc2.getOpt(key1).unsafeRunSync() shouldEqual None
      rc2.getOpt(key2).unsafeRunSync() shouldEqual None
      rc2.getOpt(key3).unsafeRunSync() shouldEqual Some(val3)

      // We can still rollback without error
      rc2.rollback(2).unsafeRunSync()
      rc2.rollback(1).unsafeRunSync()
      rc2.getOpt(key3).unsafeRunSync() shouldBe None
    }

  }

  override protected def afterEach(): Unit =
    db.clear().unsafeRunSync()
}
