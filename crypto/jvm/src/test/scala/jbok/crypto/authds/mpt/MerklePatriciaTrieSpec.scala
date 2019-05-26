package jbok.crypto.authds.mpt

import cats.effect.{IO, Resource}
import cats.implicits._
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.common.testkit._
import jbok.common.{CommonSpec, FileUtil}
import jbok.crypto.authds.mpt.MptNode.{BranchNode, ExtensionNode, LeafNode}
import jbok.persistent.rocksdb.RocksKVStore
import jbok.persistent.{ColumnFamily, MemoryKVStore}
import org.scalacheck.Gen
import scodec.bits._

import scala.util.Random

class MerklePatriciaTrieSpec extends CommonSpec {

  def test(name: String, resource: Resource[IO, MerklePatriciaTrie[IO, String, String]]): Unit = {
    s"MPT ${name}" should {
      "codec round trip" in {
        val leafNode = LeafNode("dead", hex"beef")
        RlpCodec.decode[MptNode](leafNode.bytes.bits).require.value shouldBe leafNode
        leafNode.bytes.length shouldBe 1 + (1 + 1 + 2) + (1 + 2)

        val extNode = ExtensionNode("babe", leafNode.entry)
        RlpCodec.decode[MptNode](extNode.bytes.bits).require.value shouldBe extNode
        RlpCodec.decode[MptNode](extNode.bytes.bits).require.value.asInstanceOf[ExtensionNode].child shouldBe Right(leafNode)
        extNode.bytes.length shouldBe 1 + (1 + 1 + 2) + (1 + leafNode.bytes.length)

        val branchNode = BranchNode.withSingleBranch('a', extNode.entry, Some(hex"c0de"))
        val bn         = RlpCodec.decode[MptNode](branchNode.bytes.bits).require.value.asInstanceOf[BranchNode]
        bn shouldBe branchNode
        bn.branchAt('a') shouldBe Some(extNode.entry)
        bn.bytes.length shouldBe 1 + (15 * 1) + (1 + extNode.bytes.length) + (1 + 2)
      }

      "put and get" in withResource(resource) { mpt =>
        val kvs = List(
          ""     -> "83b",
          "dc17" -> "a",
          "b07d" -> "",
          "9"    -> "94e5",
          "a867" -> "0",
          "c5"   -> "fa01",
          "7c"   -> "c",
          "de8a" -> "",
          "5eb"  -> "d869",
          "b395" -> "",
          "7"    -> "f",
          "3"    -> "",
          "d"    -> "8491"
        )

        kvs.foreach { case (k, v) => mpt.put(k, v).unsafeRunSync() }
        kvs.foreach { case (k, v) => mpt.get(k).unsafeRunSync() shouldBe Some(v) }
        mpt.toMap.map(_ shouldBe kvs.toMap)
      }

      "mustGet empty root & hash" in withResource(resource) { mpt =>
        for {
          hash <- mpt.getRootHash
          _ = hash shouldBe MerklePatriciaTrie.emptyRootHash
          _ <- mpt.getNodeByHash(hash).map(_ shouldBe None)
        } yield ()
      }

      "put leaf node when empty" in withResource(resource) { mpt =>
        for {
          _   <- mpt.put("leafKey", "leafValue")
          res <- mpt.get("leafKey")
          _ = res shouldBe Some("leafValue")
        } yield ()
      }

      "put large key and value" in withResource(resource) { mpt =>
        val key   = genHex(0, 1024).sample.get
        val value = genHex(1024, 2048).sample.get
        for {
          _   <- mpt.put(key, value)
          res <- mpt.get(key)
          _ = res shouldBe Some(value)
        } yield ()
      }

      "put and mustGet empty key" in withResource(resource) { mpt =>
        for {
          _   <- mpt.put("", "")
          _   <- mpt.put("", "1")
          res <- mpt.get("")
          _ = res shouldBe Some("1")
        } yield ()
      }

      "have same root on different orders of insertion" in {
        forAll { m: Map[String, String] =>
          val kvs = m.toList
          resource
            .use { mpt =>
              for {
                _  <- kvs.traverse { case (k, v) => mpt.put(k, v) }
                h1 <- mpt.getRootHash
                kvs2 = Random.shuffle(kvs)
                _  <- kvs2.traverse { case (k, v) => mpt.put(k, v) }
                h2 <- mpt.getRootHash
                _ = h1 shouldBe h2
              } yield ()
            }
            .unsafeRunSync()
        }
      }

      "remove key from an empty tree" in withResource(resource) { mpt =>
        mpt.del("1").unsafeRunSync()
        mpt.getRootHash.map(_ shouldBe MerklePatriciaTrie.emptyRootHash)
      }

      "remove a key that does not exist" in withResource(resource) { mpt =>
        for {
          _ <- mpt.put("1", "5")
          _ <- mpt.get("1").map(_ shouldBe Some("5"))
          _ <- mpt.del("2")
          _ <- mpt.get("1").map(_ shouldBe Some("5"))
        } yield ()
      }

      "perform as an immutable data structure" in {
        forAll { (m1: Map[String, String], m2: Map[String, String]) =>
          resource
            .use { mpt =>
              for {
                _     <- m1.toList.traverse { case (k, v) => mpt.put(k, v) }
                root1 <- mpt.getRootHash
                _     <- m2.toList.traverse { case (k, v) => mpt.put(k, v) }
                res   <- mpt.toMap
                _ = res shouldBe m1 ++ m2
                _   <- mpt.rootHash.set(Some(root1))
                res <- mpt.toMap
                _ = res shouldBe m1
              } yield ()
            }
            .unsafeRunSync()
        }
      }

      "perform as an immutable data structure with deletion" in {
        forAll { (m1: Map[String, String], m2: Map[String, String]) =>
          resource
            .use { mpt =>
              for {
                _     <- m1.toList.traverse { case (k, v) => mpt.put(k, v) }
                root1 <- mpt.getRootHash
                _     <- m2.toList.traverse { case (k, v) => mpt.del(k) }
                res   <- mpt.toMap.map(_.keySet)
                _ = res shouldBe (m1.keySet -- m2.keySet)
                _   <- mpt.rootHash.set(Some(root1))
                res <- mpt.toMap
                _ = res shouldBe m1
              } yield ()
            }
            .unsafeRunSync()
        }
      }
    }
  }

  val memory = Resource.liftF(for {
    store <- MemoryKVStore[IO]
    mpt   <- MerklePatriciaTrie[IO, String, String](ColumnFamily.default, store)
  } yield mpt)

  val rocksdb = for {
    file  <- FileUtil[IO].temporaryDir()
    store <- RocksKVStore.resource[IO](file.path, List(ColumnFamily.default))
    mpt   <- Resource.liftF(MerklePatriciaTrie[IO, String, String](ColumnFamily.default, store))
  } yield mpt

  test("memory", memory)
  test("rocksdb", rocksdb)
}
