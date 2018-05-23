package jbok.crypto.hashing

import jbok.JbokSpec
import jbok.crypto._
import scodec.Codec

class HashingSpec extends JbokSpec {
  "hashing" should {
    "generate correct digest" in {
      val str = "multihash"

      val hash1 = str.utf8bytes.hashed(Hashing.sha1)
      hash1.bytes.toHex shouldBe "111488c2f11fb2ce392acb5b2986e640211c4690073e"

      val hash2 = str.utf8bytes.hashed(Hashing.sha256)
      hash2.bytes.toHex shouldBe "12209cbc07c3f991725836a3aa2a581ca2029198aa420b9d99bc0e131d9f3e2cbe47"
    }

    "generate hash" in {
      def check[H](implicit H: Hashing[H]) = {
        forAll() { (s1: String, s2: String) =>
          val hash1 = s1.utf8bytes.hashed
          val hash2 = s2.utf8bytes.hashed
          (s1 == s2) shouldBe (hash1 == hash2)
        }
      }

      check(Hashing.sha1)
      check(Hashing.sha256)
      check(Hashing.sha512)
      check(Hashing.blake2b)
    }

    "encode and decode" in {
      def check[H](implicit H: Hashing[H]) = {
        forAll() { s: String =>
          val bytes = s.utf8bytes
          val hash = bytes.hashed[H]
          val encoded = Codec.encode(hash).require
          val decoded = Codec.decode[Hash](encoded).require.value
          decoded shouldBe hash
          decoded.hasher.hash(bytes) shouldBe hash
        }
      }

      check(Hashing.sha1)
      check(Hashing.sha256)
      check(Hashing.sha512)
      check(Hashing.blake2b)
    }
  }
}
