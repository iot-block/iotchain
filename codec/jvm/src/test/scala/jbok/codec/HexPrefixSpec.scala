package jbok.codec

import jbok.codec.HexPrefix.Nibbles
import jbok.common.CommonSpec
import org.scalacheck.Gen
import scodec.bits._

class HexPrefixSpec extends CommonSpec {
  "HexPrefix" should {
    val charGen: Gen[Char] = Gen.oneOf("0123456789abcdef")
    val hexGen: Gen[String] = for {
      size  <- Gen.chooseNum(0, 100)
      chars <- Gen.listOfN(size, charGen)
    } yield chars.mkString

    val boolGen: Gen[Boolean] = Gen.oneOf(true, false)

    "encode and decode nibbles" in {
      HexPrefix.encode(Nibbles.coerce("12345"), isLeaf = false) shouldBe hex"112345"
      HexPrefix.encode(Nibbles.coerce("012345"), isLeaf = false) shouldBe hex"00012345"
      HexPrefix.encode(Nibbles.coerce("f1cb8"), isLeaf = true) shouldBe hex"3f1cb8"
      HexPrefix.encode(Nibbles.coerce("0f1cb8"), isLeaf = true) shouldBe hex"200f1cb8"

      HexPrefix.decode(hex"00abcd").require shouldBe ((false, Nibbles.coerce("abcd")))
      HexPrefix.decode(hex"20abcd").require shouldBe ((true, Nibbles.coerce("abcd")))
      HexPrefix.decode(hex"19abcd").require shouldBe ((false, Nibbles.coerce("9abcd")))
      HexPrefix.decode(hex"39abcd").require shouldBe ((true, Nibbles.coerce("9abcd")))

      forAll(hexGen, boolGen) {
        case (hex, isLeaf) =>
          val nibbles = Nibbles.coerce(hex)
          val bytes   = HexPrefix.encode(nibbles, isLeaf = isLeaf)
          val decoded = HexPrefix.decode(bytes).require
          decoded shouldBe ((isLeaf, nibbles))
      }
    }
  }
}
