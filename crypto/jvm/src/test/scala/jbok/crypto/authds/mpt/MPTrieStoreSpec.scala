package jbok.crypto.authds.mpt

import cats.effect.IO
import jbok.JbokSpec
import jbok.common.testkit.HexGen
import jbok.crypto.authds.mpt.Node.BlankNode
import org.scalacheck.Gen
import jbok.codec.rlp.codecs._

class MPTrieStoreSpec extends JbokSpec {
  class Setup {
    val trie = MPTrieStore.inMemory[IO, String, String].unsafeRunSync()
  }

  "merkle patricia trie store" should {
    val kvsGen = for {
      n <- Gen.chooseNum(0, 32)
      size <- Gen.chooseNum(0, 100)
    } yield (1 to n).toList.map(_ => HexGen.genHex(0, size).sample.get -> HexGen.genHex(0, size).sample.get).toMap

    "get empty root & hash" in new Setup {
      val hash = trie.getRootHash.unsafeRunSync()
      hash shouldBe MPTrie.emptyRootHash
      trie.getNodeByHash(hash).unsafeRunSync() shouldBe BlankNode
    }

    "put leaf node when empty" in new Setup {
      trie.put("leafKey", "leafValue").unsafeRunSync()
      trie.get("leafKey").unsafeRunSync() shouldBe "leafValue"
    }

    "put large key and value" in new Setup {
      val key = HexGen.genHex(0, 1024).sample.get
      val value = HexGen.genHex(1024, 2048).sample.get
      trie.put(key, value).unsafeRunSync()
      trie.getOpt(key).unsafeRunSync() shouldBe Some(value)
    }

    "put and get empty key" in new Setup {
      trie.put("", "").unsafeRunSync()
      trie.put("", "1").unsafeRunSync()
      trie.getOpt("").unsafeRunSync() shouldBe Some("1")
    }

    "put and get" in new Setup {
      forAll(kvsGen) { m =>
        val kvs = m.toList
        kvs.foreach { case (k, v) => trie.put(k, v).unsafeRunSync() }
        kvs.foreach { case (k, v) => trie.getOpt(k).unsafeRunSync() shouldBe Some(v) }
        trie.toMap.unsafeRunSync() shouldBe kvs.toMap
        trie.clear().unsafeRunSync()
      }
    }

    "have same root on differnt orders of insertion" in {}

    "Remove key from an empty tree" ignore {}

    "Remove a key that does not exist" ignore {}

    "Insert only one (key, value) pair to a trie and then deleted" ignore {}

    "Insert two (key, value) pairs with the first hex not in common" ignore {}

    "Insert two (key, value) pairs with one hex or more in common" ignore {}
  }
}
