package jbok.codec

import jbok.JbokSpec
import org.scalacheck.Gen
import scodec.bits._

class HexPrefixSpec extends JbokSpec {
  "hex-prefix" should {
    val charGen: Gen[Char] = Gen.oneOf("0123456789abcdef")
    val hexGen: Gen[String] = for {
      size <- Gen.chooseNum(0, 100)
      chars <- Gen.listOfN(size, charGen)
    } yield chars.mkString

    val boolGen: Gen[Boolean] = Gen.oneOf(true, false)

    "encode and decode nibbles" in {
      HexPrefix.encode("12345", isLeaf = false) shouldBe hex"112345"
      HexPrefix.encode("012345", isLeaf = false) shouldBe hex"00012345"
      HexPrefix.encode("f1cb8", isLeaf = true) shouldBe hex"3f1cb8"
      HexPrefix.encode("0f1cb8", isLeaf = true) shouldBe hex"200f1cb8"

      HexPrefix.decode(hex"00abcd").require shouldBe ((false, "abcd"))
      HexPrefix.decode(hex"20abcd").require shouldBe ((true, "abcd"))
      HexPrefix.decode(hex"19abcd").require shouldBe ((false, "9abcd"))
      HexPrefix.decode(hex"39abcd").require shouldBe ((true, "9abcd"))

      forAll(hexGen, boolGen) {
        case (hex, isLeaf) =>
          val bytes = HexPrefix.encode(hex, isLeaf = isLeaf)
          val decoded = HexPrefix.decode(bytes).require
          decoded shouldBe ((isLeaf, hex))
      }
    }
  }
}
