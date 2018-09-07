package jbok.crypto.authds.mpt

import cats.effect.IO
import jbok.JbokSpec
import jbok.crypto.authds.mpt.Node.{BranchNode, ExtensionNode, LeafNode}
import jbok.persistent.KeyValueDB
import scodec.bits._

class MPTrieSpec extends JbokSpec {
  trait Setup {
    val db = KeyValueDB.inMemory[IO].unsafeRunSync()
    val trie = MPTrie[IO](db).unsafeRunSync()
  }

  "codec round trip" in {
    val leafNode = LeafNode("dead", hex"beef")
    NodeCodec.decode(leafNode.bytes).require shouldBe leafNode
    leafNode.bytes.length shouldBe 1 + (1 + 1 + 2) + (1 + 2)

    val extNode = ExtensionNode("babe", leafNode.entry)
    NodeCodec.decode(extNode.bytes).require shouldBe extNode
    NodeCodec.decode(extNode.bytes).require.asInstanceOf[ExtensionNode].child shouldBe Right(leafNode)
    extNode.bytes.length shouldBe 1 + (1 + 1 + 2) + (1 + leafNode.bytes.length)

    val branchNode = BranchNode.withSingleBranch('a', extNode.entry, Some(hex"c0de"))
    val bn = NodeCodec.decode(branchNode.bytes).require.asInstanceOf[BranchNode]
    bn shouldBe branchNode
    bn.branchAt('a') shouldBe Some(extNode.entry)
    bn.bytes.length shouldBe 1 + (15 * 1) + (1 + extNode.bytes.length) + (1 + 2)
  }

  "simple put and get" in new Setup {
    trie.getRootOpt.unsafeRunSync() shouldBe None
    trie.getRootHash.unsafeRunSync() shouldBe MPTrie.emptyRootHash
    trie.put(hex"cafe", hex"babe").unsafeRunSync()
    trie.getRootOpt.unsafeRunSync() shouldBe Some(LeafNode("cafe", hex"babe"))
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
