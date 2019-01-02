package jbok.persistent

import cats.effect.IO
import jbok.common.execution._
import cats.implicits._
import jbok.JbokSpec
import jbok.codec.rlp.implicits._
import scodec.bits.ByteVector
import jbok.common.testkit._

class KeyValueDBSpec extends JbokSpec {
  def check(backend: String, path: String) = {
    s"KeyValueDB of ${backend}" should {
      "toMap" in {
        val ns1 = random[ByteVector]
        val ns2 = random[ByteVector]
        forAll { (kv1: Map[String, String], kv2: Map[String, String]) =>
          val db  = KeyValueDB.forBackendAndPath[IO](backend, path).unsafeRunSync()
          kv1.toList.traverse[IO, Unit](t => db.put(t._1, t._2, ns1)).unsafeRunSync()
          kv2.toList.traverse[IO, Unit](t => db.put(t._1, t._2, ns2)).unsafeRunSync()
          db.toMap[String, String](ns1).unsafeRunSync() shouldBe kv1
          db.toMap[String, String](ns2).unsafeRunSync() shouldBe kv2
        }
      }

      "keys" in {
        val ns1 = genBoundedByteVector(0, 1024).sample.get
        val ns2 = genBoundedByteVector(0, 1024).sample.get
        forAll { (kv1: Map[String, String], kv2: Map[String, String]) =>
          val db  = KeyValueDB.forBackendAndPath[IO](backend, path).unsafeRunSync()
          kv1.toList.traverse[IO, Unit](t => db.put(t._1, t._2, ns1)).unsafeRunSync()
          kv2.toList.traverse[IO, Unit](t => db.put(t._1, t._2, ns2)).unsafeRunSync()
          db.keys[String](ns1).unsafeRunSync() should contain theSameElementsAs kv1.keys.toList
          db.keys[String](ns2).unsafeRunSync() should contain theSameElementsAs kv2.keys.toList
          db.keysRaw.unsafeRunSync().length shouldBe kv1.size + kv2.size
        }
      }

      "writeBatch" in {
        forAll { (kvs: Map[String, String], namespace: ByteVector) =>
          val db  = KeyValueDB.forBackendAndPath[IO](backend, path).unsafeRunSync()
          db.writeBatch[String, String](kvs.toList.map(t => t._1 -> t._2.some), namespace).unsafeRunSync()
          db.toMap[String, String](namespace).unsafeRunSync() shouldBe kvs
        }
      }
    }
  }

  check(KeyValueDB.INMEM, "")
}
