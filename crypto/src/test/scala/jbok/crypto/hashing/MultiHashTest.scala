package jbok.crypto.hashing

import cats.Id
import jbok.crypto.PropertyTest
import scodec.Codec
import tsec.hashing.CryptoHasher

class MultiHashTest extends PropertyTest {
  val str = "multihash"

  test("test hashing") {
    val hash1 = MultiHash.hash(str, HashType.sha1)
    Codec.encode(hash1).require.toHex shouldBe "111488c2f11fb2ce392acb5b2986e640211c4690073e"

    val hash2 = MultiHash.hash(str, HashType.sha256)
    Codec.encode(hash2).require.toHex shouldBe "12209cbc07c3f991725836a3aa2a581ca2029198aa420b9d99bc0e131d9f3e2cbe47"
  }

  def check[A](ht: HashType[A])(implicit ev: CryptoHasher[Id, A]) =
    test(s"test with ${ht.name}") {
      forAll { (s1: String, s2: String) =>
        val hash1 = MultiHash.hash(s1, ht)
        val hash2 = MultiHash.hash(s2, ht)

        (hash1.digest == hash2.digest) shouldBe (s1 == s2)
      }
    }

  check(HashType.sha256)
  check(HashType.sha1)
  check(HashType.sha512)
  check(HashType.blake2b)
}
