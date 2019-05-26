package jbok.persistent
import cats.effect.{IO, Resource}
import jbok.common.{CommonSpec, FileUtil}
import jbok.persistent.rocksdb.RocksKVStore
import scodec.bits.ByteVector
import jbok.codec.rlp.implicits._

class StageKVStoreSpec extends CommonSpec {
  val default = ColumnFamily.default
  val cfa     = ColumnFamily("a")
  val cfb     = ColumnFamily("b")
  val cfs     = List(default, cfa, cfb)

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

  val rocks = FileUtil[IO].temporaryDir().flatMap { dir =>
    RocksKVStore.resource[IO](dir.path, cfs).map(inner => StageKVStore(SingleColumnKVStore[IO, ByteVector, ByteVector](default, inner)))
  }

  val memory =
    Resource.liftF(MemoryKVStore[IO]).map(inner => StageKVStore(SingleColumnKVStore[IO, ByteVector, ByteVector](default, inner)))

  test("rocksdb", rocks)
  test("memory", memory)
}
