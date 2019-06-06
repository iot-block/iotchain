package jbok.persistent.mpt

import cats.effect.{IO, Resource}
import cats.implicits._
import jbok.codec.HexPrefix.Nibbles
import jbok.codec.rlp.implicits._
import jbok.codec.testkit._
import jbok.common.{CommonSpec, FileUtil}
import jbok.persistent.mpt.MptNode.{BranchNode, ExtensionNode, LeafNode}
import jbok.persistent.rocksdb.RocksKVStore
import jbok.persistent.{ColumnFamily, MemoryKVStore}
import org.scalacheck.Gen
import scodec.bits._

import scala.util.Random

class MerklePatriciaTrieSpec extends CommonSpec {

  def test(name: String, resource: Resource[IO, MerklePatriciaTrie[IO, ByteVector, ByteVector]]): Unit = {
    s"MPT ${name}" should {
      "codec round trip" in {
        val leafNode: MptNode = LeafNode(Nibbles.coerce("dead"), hex"beef".encoded)
        roundtripAndMatch(leafNode, hex"0xc78320dead82beef")

        val extNode: MptNode = ExtensionNode(Nibbles.coerce("babe"), leafNode.entry)
        roundtripAndMatch(extNode, hex"0xcc8300babec78320dead82beef")

        val branchNode: MptNode = BranchNode.withSingleBranch('a', extNode.entry, hex"c0de".encoded.some)
        roundtripAndMatch(branchNode, hex"0xdf80808080808080808080cc8300babec78320dead82beef808080808082c0de")
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
        ).map { case (k, v) => ByteVector.fromValidHex(k) -> ByteVector.fromValidHex(v) }

        kvs.foreach { case (k, v) => mpt.put(k, v).unsafeRunSync() }
        kvs.foreach { case (k, v) => mpt.get(k).unsafeRunSync() shouldBe Some(v) }
        mpt.toMap.map(_ shouldBe kvs.toMap).void
      }

      "get empty root & hash" in withResource(resource) { mpt =>
        for {
          hash <- mpt.getRootHash
          _ = hash shouldBe MerklePatriciaTrie.emptyRootHash
          _ <- mpt.getNodeByHash(hash).map(_ shouldBe None)
        } yield ()
      }

      "put leaf node when empty" in withResource(resource) { mpt =>
        for {
          _   <- mpt.put(hex"cafe", hex"babe")
          res <- mpt.get(hex"cafe")
          _ = res shouldBe Some(hex"babe")
        } yield ()
      }

      "put large key and value" in withResource(resource) { mpt =>
        val key   = ByteVector(random[List[Byte]](Gen.listOfN(4096, arbByte.arbitrary)))
        val value = ByteVector(random[List[Byte]](Gen.listOfN(2048, arbByte.arbitrary)))
        for {
          _   <- mpt.put(key, value)
          res <- mpt.get(key)
          _ = res shouldBe Some(value)
        } yield ()
      }

      "put and get empty key" in withResource(resource) { mpt =>
        for {
          _   <- mpt.put(ByteVector.empty, ByteVector.empty)
          _   <- mpt.put(ByteVector.empty, hex"1")
          res <- mpt.get(ByteVector.empty)
          _ = res shouldBe Some(hex"1")
        } yield ()
      }

      "have same root on different orders of insertion" in {
        forAll { m: Map[ByteVector, ByteVector] =>
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
        mpt.del(hex"1").unsafeRunSync()
        mpt.getRootHash.map(_ shouldBe MerklePatriciaTrie.emptyRootHash).void
      }

      "remove a key that does not exist" in withResource(resource) { mpt =>
        for {
          _ <- mpt.put(hex"1", hex"5")
          _ <- mpt.get(hex"1").map(_ shouldBe Some(hex"5"))
          _ <- mpt.del(hex"2")
          _ <- mpt.get(hex"1").map(_ shouldBe Some(hex"5"))
        } yield ()
      }

      "perform as an immutable data structure" in {
        forAll { (m1: Map[ByteVector, ByteVector], m2: Map[ByteVector, ByteVector]) =>
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
        forAll { (m1: Map[ByteVector, ByteVector], m2: Map[ByteVector, ByteVector]) =>
          resource
            .use { mpt =>
              for {
                _     <- m1.toList.traverse { case (k, v) => mpt.put(k, v) }
                root1 <- mpt.getRootHash
                _     <- m2.toList.traverse { case (k, _) => mpt.del(k) }
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
    mpt   <- MerklePatriciaTrie[IO, ByteVector, ByteVector](ColumnFamily.default, store)
  } yield mpt)

  val rocksdb = for {
    file  <- FileUtil[IO].temporaryDir()
    store <- RocksKVStore.resource[IO](file.path, List(ColumnFamily.default))
    mpt   <- Resource.liftF(MerklePatriciaTrie[IO, ByteVector, ByteVector](ColumnFamily.default, store))
  } yield mpt

  test("memory", memory)
  test("rocksdb", rocksdb)
}
