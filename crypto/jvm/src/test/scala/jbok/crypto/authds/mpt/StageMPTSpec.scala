package jbok.crypto.authds.mpt

import cats.effect.{IO, Resource}
import jbok.codec.rlp.implicits._
import jbok.common.{CommonSpec, FileUtil}
import jbok.persistent._
import jbok.persistent.rocksdb.RocksKVStore

class StageMPTSpec extends CommonSpec {

  def check(name: String, resource: Resource[IO, StageKVStore[IO, String, String]]): Unit =
    s"Staged MPT ${name}" should {
      "not write inserts until commit" in withResource(resource) { stage =>
        val updated = stage
          .put("1", "1")

        for {
          res <- updated.get("1")
          _ = res shouldBe Some("1")

          res <- updated.inner.get("1")
          _ = res shouldBe None

          res <- stage.get("1")
          _ = res shouldBe None

          res <- stage.inner.get("1")
          _ = res shouldBe None

          committed <- updated.commit

          res <- committed.inner.get("1")
          _ = res shouldBe Some("1")

          res <- stage.get("1")
          _ = res shouldBe Some("1")
        } yield ()
      }
    }

  val memory = Resource.liftF(for {
    store <- MemoryKVStore[IO]
    mpt   <- MerklePatriciaTrie[IO, String, String](ColumnFamily.default, store)
    stage = StageKVStore(mpt)
  } yield stage)

  val rocksdb = for {
    file  <- FileUtil[IO].temporaryDir()
    store <- RocksKVStore.resource[IO](file.path, List(ColumnFamily.default))
    mpt   <- Resource.liftF(MerklePatriciaTrie[IO, String, String](ColumnFamily.default, store))
    stage = StageKVStore(mpt)
  } yield stage

  check("memory", memory)
  check("rocksdb", rocksdb)

}
