package jbok.crypto.authds.mpt

import cats.effect.IO
import cats.implicits._
import jbok.JbokSpec
import jbok.codec._
import jbok.crypto.authds.mpt.Node.{BlankNode, BranchNode, ExtensionNode, LeafNode}
import scodec.bits._

class MPTrieSpec extends JbokSpec {
  class Setup {
    val trie = MPTrie.inMemory[IO]().unsafeRunSync()
  }

  "codec round trip" in {
    NodeCodec.decode(BlankNode.bytes).require shouldBe BlankNode

    val leafNode = LeafNode("leafKey".utf8Bytes.toHex, "leafValue".utf8Bytes)
    NodeCodec.decode(leafNode.bytes).require shouldBe leafNode

    val extNode = ExtensionNode("extKey".utf8Bytes.toHex, leafNode.entry)
    NodeCodec.decode(extNode.bytes).require shouldBe extNode
    NodeCodec.decode(extNode.bytes).require.asInstanceOf[ExtensionNode].child shouldBe Right(leafNode)

    val branchNode = BranchNode.withSingleBranch('a', extNode.entry, "branchValue".utf8Bytes.some)
    val bn = NodeCodec.decode(branchNode.bytes).require.asInstanceOf[BranchNode]
    bn shouldBe branchNode
    bn.branchAt('a') shouldBe extNode.entry
  }

  "simple put and get" in new Setup {
    trie.getRoot.unsafeRunSync() shouldBe BlankNode
    trie.getRootHash.unsafeRunSync() shouldBe MPTrie.emptyRootHash
    trie.put(hex"cafe", hex"babe").unsafeRunSync()
    trie.getRoot.unsafeRunSync() shouldBe LeafNode("cafe", hex"babe")
    trie.getRootHash.unsafeRunSync() shouldBe LeafNode("cafe", hex"babe").hash

    trie.keys.unsafeRunSync() shouldBe List(hex"cafe")
    trie.toMap.unsafeRunSync() shouldBe Map(hex"cafe" -> hex"babe")
  }

  "put and get" in new Setup {
    val kvs = List(
      "" -> "83b",
      "dc17" -> "a",
      "b07d" -> "",
      "9" -> "94e5",
      "a867" -> "0",
      "c5" -> "fa01",
      "7c" -> "c",
      "de8a" -> "",
      "5eb" -> "d869",
      "b395" -> "",
      "7" -> "f",
      "3" -> "",
      "d" -> "8491"
    ).map(t => (ByteVector.fromValidHex(t._1), ByteVector.fromValidHex(t._2)))

    kvs.foreach { case (k, v) => trie.put(k, v).unsafeRunSync() }
    kvs.foreach { case (k, v) => trie.getOpt(k).unsafeRunSync() shouldBe Some(v) }
    trie.toMap.unsafeRunSync() shouldBe kvs.toMap
  }
}
