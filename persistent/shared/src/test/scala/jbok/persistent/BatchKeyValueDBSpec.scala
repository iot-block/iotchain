package jbok.persistent

import cats.effect.IO
import jbok.common.testkit.ByteGen
import scodec.bits.ByteVector

//class BatchKeyValueDBSpec extends JbokSpec {
//  def check(db: BatchKeyValueDB[IO]): Unit =
//    s"batched ${db.kv.getClass.getSimpleName}" should {
//      val bytesGen = ByteGen.genBoundedBytes(0, 1024).map(ByteVector.apply)
//      val kvs: List[(ByteVector, ByteVector)] =
//        (1 to 20).toList.map(_ => bytesGen.sample.get -> bytesGen.sample.get)
//
//      "be able to insert and retrieve" in {
//        kvs.foreach {
//          case (k, v) =>
//            db.addPut(k, v).unsafeRunSync()
//        }
//
//        db.commit().unsafeRunSync()
//
//        kvs.foreach {
//          case (k, v) =>
//            db.get(k).unsafeRunSync() shouldBe v
//        }
//
//        db.clear().unsafeRunSync()
//      }
//
//      "remove keys" in {
//        kvs.foreach {
//          case (k, v) =>
//            db.addPut(k, v).unsafeRunSync()
//            db.addDel(k).unsafeRunSync()
//        }
//
//        db.commit().unsafeRunSync()
//
//        kvs.foreach {
//          case (k, v) =>
//            db.getOpt(k).unsafeRunSync() shouldBe None
//        }
//
//        db.clear().unsafeRunSync()
//      }
//    }
//}
