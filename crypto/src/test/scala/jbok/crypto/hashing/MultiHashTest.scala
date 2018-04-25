package jbok.crypto.hashing

import org.scalatest.{FunSuite, Matchers}
import scodec.Codec

class MultiHashTest extends FunSuite with Matchers {
  val str = "multihash"

  test("test hashing") {
    val hash1 = MultiHash.hash(str, HashType.sha1)
    Codec.encode(hash1).require.toHex shouldBe "111488c2f11fb2ce392acb5b2986e640211c4690073e"

    val hash2 = MultiHash.hash(str, HashType.sha256)
    Codec.encode(hash2).require.toHex shouldBe "12209cbc07c3f991725836a3aa2a581ca2029198aa420b9d99bc0e131d9f3e2cbe47"
  }
}
