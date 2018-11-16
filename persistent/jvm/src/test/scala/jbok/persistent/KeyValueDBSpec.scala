package jbok.persistent
import cats.effect.IO
import cats.implicits._
import jbok.JbokSpec
import jbok.codec.rlp.implicits._
import jbok.persistent.testkit._
import scodec.bits.ByteVector
import jbok.common.testkit._

class KeyValueDBSpec extends JbokSpec {
  "KeyValueDB" should {
    "toMap" in {
      forAll { (db: KeyValueDB[IO], kvs: Map[String, String], namespace: ByteVector) =>
        kvs.toList.traverse[IO, Unit](t => db.put(t._1, t._2, namespace)).unsafeRunSync()
        db.toMap[String, String](namespace).unsafeRunSync() shouldBe kvs
      }
    }

    "keys" in {
      forAll { (db: KeyValueDB[IO], kvs: Map[String, String], namespace: ByteVector) =>
        kvs.toList.traverse[IO, Unit](t => db.put(t._1, t._2, namespace)).unsafeRunSync()
        db.keys[String](namespace).unsafeRunSync() should contain theSameElementsAs kvs.keys.toList
      }
    }

    "writeBatch" in {
      forAll { (db: KeyValueDB[IO], kvs: Map[String, String], namespace: ByteVector) =>
        db.writeBatch[String, String](kvs.toList.map(t => t._1 -> t._2.some), namespace).unsafeRunSync()
        db.toMap[String, String](namespace).unsafeRunSync() shouldBe kvs
      }
    }
  }
}
