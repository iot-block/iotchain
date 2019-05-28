package jbok.persistent

import cats.effect.{IO, Resource}
import jbok.common.CommonSpec
import scodec.bits.ByteVector
import jbok.persistent.testkit._

class StageKVStoreSpec extends CommonSpec {
  def test(name: String, resource: Resource[IO, StageKVStore[IO, ByteVector, ByteVector]]): Unit =
    s"StageKVStore $name" should {
      val key   = ByteVector("key".getBytes)
      val value = ByteVector("value".getBytes)

      "not write inserts until commit" in withResource(resource) { stage =>
        val updated = stage
          .put(key, value)

        for {
          res <- updated.mustGet(key)
          _ = res shouldBe value

          res <- updated.inner.get(key)
          _ = res shouldBe None

          committed <- updated.commit

          res <- committed.inner.get(key)
          _ = res shouldBe Some(value)

          res <- stage.mustGet(key)
          _ = res shouldBe value
        } yield ()
      }
    }

  test("rocksdb", testRocksStageStore())
  test("memory", testMemoryStageStore)
}
