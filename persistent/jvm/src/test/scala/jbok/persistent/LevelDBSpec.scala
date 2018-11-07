//package jbok.persistent
//
//import cats.effect.IO
//import jbok.JbokSpec
//import jbok.common.testkit.ByteGen
//import jbok.persistent.leveldb.LevelDB
//import scodec.bits.ByteVector
//import better.files._
//
//class LevelDBSpec extends JbokSpec {
//  "LevelDB" should {
//    val bytesGen = ByteGen.genBoundedBytes(0, 1024).map(ByteVector.apply)
//
//    "be able to insert and retrieve" in {
//      val tmp = File.newTemporaryDirectory()
//      println(tmp.pathAsString)
//      val db  = LevelDB[IO](tmp.pathAsString).unsafeRunSync()
//      forAll(bytesGen, bytesGen) { (key, value) =>
//        db.put(key, value).unsafeRunSync()
//        db.get(key).unsafeRunSync() shouldBe value
//        db.getOpt(key).unsafeRunSync() shouldBe Some(value)
//      }
//      tmp.delete()
//    }
//
//    "remove keys" in {
//      val tmp = File.newTemporaryDirectory()
//      println(tmp.pathAsString)
//      val db  = LevelDB[IO](tmp.pathAsString).unsafeRunSync()
//      forAll(bytesGen, bytesGen) { (key, value) =>
//        db.put(key, value).unsafeRunSync()
//        db.get(key).unsafeRunSync() shouldBe value
//        db.del(key).unsafeRunSync()
//        db.getOpt(key).unsafeRunSync() shouldBe None
//      }
//      tmp.delete()
//    }
//  }
//}
