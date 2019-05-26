package jbok.persistent

import cats.effect.{IO, Resource}
import cats.implicits._
import jbok.common.{CommonSpec, FileUtil}
import jbok.persistent.rocksdb.RocksKVStore
import scodec.bits.ByteVector
import jbok.common.testkit._

class KVStoreSpec extends CommonSpec {
  val default = ColumnFamily.default
  val cfa     = ColumnFamily("a")
  val cfb     = ColumnFamily("b")
  val cfs     = List(default, cfa, cfb)

  def test(name: String, resource: Resource[IO, KVStore[IO]]): Unit =
    s"KVStore ${name}" should {
      "respect column family" in withResource(resource) { store =>
        val key = ByteVector("key".getBytes)
        val a   = ByteVector("a".getBytes)
        val b   = ByteVector("b".getBytes)
        for {
          _ <- store.put(cfa, key, a)
          _ <- store.put(cfb, key, b)

          value <- store.get(default, key)
          _ = value shouldBe None
          size <- store.size(default)
          _ = size shouldBe 0

          value <- store.get(cfa, key)
          _ = value shouldBe Some(a)
          size <- store.size(cfa)
          _ = size shouldBe 1

          value <- store.get(cfb, key)
          _ = value shouldBe Some(b)
          size <- store.size(cfb)
          _ = size shouldBe 1
        } yield ()
      }

      "write batch" in {
        val cf = default
        forAll { kvs: Map[ByteVector, ByteVector] =>
          val p = resource.use { store =>
            for {
              _   <- store.writeBatch(cf, kvs.toList, Nil)
              res <- store.toMap(cf)
              _ = res shouldBe kvs

              res <- store.size(cf)
              _ = res shouldBe kvs.size
            } yield ()
          }
          p.unsafeRunSync()
        }
      }

      "toMap" in {
        forAll { (kv1: Map[ByteVector, ByteVector], kv2: Map[ByteVector, ByteVector]) =>
          val p = resource.use { store =>
            for {
              _   <- kv1.toList.traverse(t => store.put(cfa, t._1, t._2))
              _   <- kv2.toList.traverse(t => store.put(cfb, t._1, t._2))
              res <- store.toMap(cfa)
              _ = res shouldBe kv1
              res <- store.toMap(cfb)
              _ = res shouldBe kv2
            } yield ()
          }
          p.unsafeRunSync()
        }
      }
    }

  val rocks = FileUtil[IO].temporaryDir().flatMap { dir =>
    RocksKVStore.resource[IO](dir.path, cfs)
  }

  val memory =
    Resource.liftF(MemoryKVStore[IO])

  test("rocksdb", rocks)
  test("memory", memory)
}
