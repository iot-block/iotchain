package jbok.crypto.authds.mpt

import cats.effect.IO
import jbok.JbokSpec
import jbok.codec.rlp._
import jbok.crypto._
import jbok.crypto.authds.mpt.Node._
import jbok.crypto.hashing.Hashing
import jbok.persistent.leveldb.LevelDBConfig
import org.scalacheck.{Arbitrary, Gen}
import scodec.bits._
import tsec.hashing.jca.SHA256

import scala.collection.mutable

class MerklePatriciaTrieSpec extends JbokSpec {
  "merkle patricia trie" should {
    val hexGen: Gen[String] = for {
      size <- Gen.choose(0, 6)
      bytes <- Gen.listOfN(size, Arbitrary.arbitrary[Byte]).map(xs => ByteVector(xs))
    } yield bytes.toHex

    val leafNodeGen: Gen[LeafNode] = for {
      key <- hexGen
      value <- hexGen
    } yield LeafNode(key, value.utf8bytes)

    val extensionNodeGen: Gen[ExtensionNode] = for {
      key <- hexGen
      leafNodeGen <- leafNodeGen
    } yield {
      val encoded = NodeCodec.encode(leafNodeGen).require
      if (encoded.size <= 32) {
        ExtensionNode(key, Right(leafNodeGen))
      } else {
        ExtensionNode(key, Left(Hashing[SHA256].hash(encoded).digest))
      }
    }

    val entryGen: Gen[NodeEntry] = for {
      leafNode <- leafNodeGen
      extensionNode <- extensionNodeGen
      node <- Gen.oneOf[Node](BlankNode, leafNode, extensionNode)
    } yield {
      val encoded = NodeCodec.encode(node).require
      if (encoded.size <= 32) {
        Right(node)
      } else {
        Left(Hashing[SHA256].hash(encoded.bytes).digest)
      }
    }

    val branchNodeGen: Gen[BranchNode] = for {
      hex <- hexGen
      br <- Gen.listOfN(16, entryGen)
    } yield BranchNode(br, Some(hex.utf8bytes))

    "encode and decode Node" in {
      def roundtrip(node: Node): ByteVector = {
        val encoded = NodeCodec.encode(node).require
        val decoded = NodeCodec.decode(encoded).require

        node shouldBe decoded
        encoded
      }

      forAll(leafNodeGen, extensionNodeGen, branchNodeGen) {
        case (ln, en, bn) =>
          roundtrip(ln)
          roundtrip(en)
          roundtrip(bn)
      }
    }

    val m = mutable.Map[String, String]()

    "store key values" in {
      val strs1 = Gen.listOfN(100, hexGen).sample.get
      val strs2 = Gen.listOfN(100, hexGen).sample.get

      val kvs = strs1.zip(strs2)

      val trie = (for {
        store <- Store[IO, ByteVector, ByteVector](LevelDBConfig("trie"))
      } yield MerklePatriciaTrie(store)).unsafeRunSync()

      def loop(trie: MerklePatriciaTrie[IO], kvs: List[(String, String)]): MerklePatriciaTrie[IO] = kvs match {
        case (k, v) :: tail =>
          val n = trie.update(k, v.utf8bytes).unsafeRunSync()
          m.put(k, v)
          n.size.unsafeRunSync() shouldBe m.size
          n.get(k).unsafeRunSync().map(_.decodeUtf8.right.get) shouldBe m.get(k)
          loop(n, tail)

        case _ => trie
      }

      val updated = loop(trie, kvs)
      updated.toMap.unsafeRunSync().mapValues(_.decodeUtf8.right.get) shouldBe m.toMap

      val cleared = updated.clear().unsafeRunSync()
      cleared.toMap.unsafeRunSync().isEmpty shouldBe true
    }
  }
}
