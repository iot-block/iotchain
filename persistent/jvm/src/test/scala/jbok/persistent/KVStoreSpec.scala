package jbok.persistent

import cats.effect.{IO, Resource}
import cats.implicits._
import jbok.common.CommonSpec
import jbok.persistent.testkit._
import scodec.bits.ByteVector

class KVStoreSpec extends CommonSpec {
  val default = ColumnFamily.default
  val cfa     = ColumnFamily("a")
  val cfb     = ColumnFamily("b")
  val cfs     = List(default, cfa, cfb)

  def test(name: String, resource: Resource[IO, KVStore[IO]]): Unit =
    s"KVStore ${name}" should {
      "respect column family" in withResource(resource) { store =>
        val key = "key".getBytes
        val a   = "a".getBytes
        val b   = "b".getBytes
        for {
          _ <- store.put(cfa, key, a)
          _ <- store.put(cfb, key, b)

          value <- store.get(default, key)
          _ = value shouldBe None
          size <- store.size(default)
          _ = size shouldBe 0

          value <- store.get(cfa, key)
          _ = value.get shouldEqual a
          size <- store.size(cfa)
          _ = size shouldBe 1

          value <- store.get(cfb, key)
          _ = value.get shouldEqual b
          size <- store.size(cfb)
          _ = size shouldBe 1
        } yield ()
      }

      "write batch" in {
        val cf = default
        forAll { m: Map[ByteVector, ByteVector] =>
          val p = resource.use { store =>
            val kvs = m.toList.map { case (k, v) => k.toArray -> v.toArray }
            for {
              _   <- store.writeBatch(cf, kvs, Nil)
              res <- store.toList(cf)
              _ = res.map { case (k, v) => ByteVector(k) -> ByteVector(v) }.toMap shouldBe m

              res <- store.size(cf)
              _ = res shouldBe kvs.size
            } yield ()
          }
          p.unsafeRunSync()
        }
      }

      "toList" in {
        forAll { (m1: Map[ByteVector, ByteVector], m2: Map[ByteVector, ByteVector]) =>
          val kvs1 = m1.toList.map { case (k, v) => k.toArray -> v.toArray }
          val kvs2 = m2.toList.map { case (k, v) => k.toArray -> v.toArray }
          val p = resource.use { store =>
            for {
              _   <- kvs1.traverse(t => store.put(cfa, t._1, t._2))
              _   <- kvs2.traverse(t => store.put(cfb, t._1, t._2))
              res <- store.toList(cfa)
              _ = res.map { case (k, v) => ByteVector(k) -> ByteVector(v) }.toMap shouldBe m1
              res <- store.toList(cfb)
              _ = res.map { case (k, v) => ByteVector(k) -> ByteVector(v) }.toMap shouldBe m2
            } yield ()
          }
          p.unsafeRunSync()
        }
      }
    }

  test("rocksdb", testRocksKVStore(cfs))
  test("memory", testMemoryKVStore)
}
