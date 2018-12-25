package jbok.crypto.authds.mpt

import cats.effect.IO
import jbok.JbokSpec
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.common.testkit._
import jbok.crypto.authds.mpt.MptNode.{BranchNode, ExtensionNode, LeafNode}
import jbok.crypto.testkit._
import jbok.persistent.KeyValueDB
import org.scalacheck.Gen
import scodec.bits._

import scala.util.Random

class MerklePatriciaTrieSpec extends JbokSpec {
  trait Fixture {
    val db        = KeyValueDB.inmem[IO].unsafeRunSync()
    val namespace = ByteVector.empty
    val mpt      = MerklePatriciaTrie[IO](namespace, db).unsafeRunSync()
  }

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

  "simple put and get" in new Fixture {
    mpt.getRootOpt.unsafeRunSync() shouldBe None
    mpt.getRootHash.unsafeRunSync() shouldBe MerklePatriciaTrie.emptyRootHash
    mpt.putRaw(hex"cafe", hex"babe").unsafeRunSync()
    mpt.getRootOpt.unsafeRunSync() shouldBe Some(LeafNode("cafe", hex"babe"))
    mpt.getRootHash.unsafeRunSync() shouldBe LeafNode("cafe", hex"babe").hash

    mpt.keysRaw.unsafeRunSync() shouldBe List(hex"cafe")
    mpt.toMapRaw.unsafeRunSync() shouldBe Map(hex"cafe" -> hex"babe")
  }

  "put and get" in new Fixture {
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
    ).map(t => (ByteVector.fromValidHex(t._1), ByteVector.fromValidHex(t._2)))

    kvs.foreach { case (k, v) => mpt.putRaw(k, v).unsafeRunSync() }
    kvs.foreach { case (k, v) => mpt.getRaw(k).unsafeRunSync() shouldBe Some(v) }
    mpt.toMapRaw.unsafeRunSync() shouldBe kvs.toMap
  }

  val kvsGen = for {
    n    <- Gen.chooseNum(0, 32)
    size <- Gen.chooseNum(0, 100)
  } yield (1 to n).toList.map(_ => genHex(0, size).sample.get -> genHex(0, size).sample.get).toMap

  "get empty root & hash" in new Fixture {
    val hash = mpt.getRootHash.unsafeRunSync()
    hash shouldBe MerklePatriciaTrie.emptyRootHash
    mpt.getNodeByHash(hash).unsafeRunSync() shouldBe None
  }

  "put leaf node when empty" in new Fixture {
    mpt.put("leafKey", "leafValue", namespace).unsafeRunSync()
    mpt.get[String, String]("leafKey", namespace).unsafeRunSync() shouldBe "leafValue"
  }

  "put large key and value" in new Fixture {
    val key   = genHex(0, 1024).sample.get
    val value = genHex(1024, 2048).sample.get
    mpt.put(key, value, namespace).unsafeRunSync()
    mpt.getOpt[String, String](key, namespace).unsafeRunSync() shouldBe Some(value)
  }

  "put and get empty key" in new Fixture {
    mpt.put("", "", namespace).unsafeRunSync()
    mpt.put("", "1", namespace).unsafeRunSync()
    mpt.getOpt[String, String]("", namespace).unsafeRunSync() shouldBe Some("1")
  }

  "put and get string" in {
    forAll { (trie: MerklePatriciaTrie[IO], m: Map[Int, Int]) =>
      val kvs       = m.toList
      val namespace = ByteVector.empty
      kvs.foreach { case (k, v) => trie.put(k, v, namespace).unsafeRunSync() }
      kvs.foreach { case (k, v) => trie.getOpt[Int, Int](k, namespace).unsafeRunSync() shouldBe Some(v) }
      trie.toMap[Int, Int](namespace).unsafeRunSync() shouldBe m
    }
  }

  "have same root on different orders of insertion" in {
    forAll { (trie: MerklePatriciaTrie[IO], m: Map[String, String], namespace: ByteVector) =>
      val kvs = m.toList
      kvs.foreach { case (k, v) => trie.put(k, v, namespace).unsafeRunSync() }
      val h1 = trie.getRootHash.unsafeRunSync()

      val kvs2 = Random.shuffle(kvs)
      kvs2.foreach { case (k, v) => trie.put(k, v, namespace).unsafeRunSync() }
      val h2 = trie.getRootHash.unsafeRunSync()
      h1 shouldBe h2
    }
  }

  "Remove key from an empty tree" in new Fixture {
    mpt.del("1", namespace).unsafeRunSync()
    mpt.getRootHash.unsafeRunSync() shouldBe MerklePatriciaTrie.emptyRootHash
  }

  "Remove a key that does not exist" in new Fixture {
    mpt.put("1", "5", namespace).unsafeRunSync()
    mpt.get[String, String]("1", namespace).unsafeRunSync() shouldBe "5"

    mpt.del("2", namespace).unsafeRunSync()
    mpt.get[String, String]("1", namespace).unsafeRunSync() shouldBe "5"
  }

  "perform as an immutable data structure" in {
    forAll { (trie: MerklePatriciaTrie[IO], m1: Map[Int, Int], m2: Map[Int, Int], namespace: ByteVector) =>
      m1.foreach { case (k, v) => trie.put(k, v, namespace).unsafeRunSync() }
      val root1 = trie.getRootHash.unsafeRunSync()

      m2.foreach { case (k, v) => trie.put(k, v, namespace).unsafeRunSync() }

      trie.toMap[Int, Int](namespace).unsafeRunSync() shouldBe m1 ++ m2

      trie.rootHash.set(Some(root1)).unsafeRunSync()
      trie.toMap[Int, Int](namespace).unsafeRunSync() shouldBe m1
    }
  }
}
