package jbok.codec

import jbok.JbokSpec
import org.scalacheck.Gen
import scodec.bits._

class HpSpec extends JbokSpec {
  "hex-prefix" should {
    val charGen: Gen[Char] = Gen.oneOf("0123456789abcdef")
    val hexGen: Gen[String] = for {
      size <- Gen.chooseNum(0, 100)
      chars <- Gen.listOfN(size, charGen)
    } yield chars.mkString

    val boolGen: Gen[Boolean] = Gen.oneOf(true, false)

    "encode and decode nibbles" in {
      hp.encode(BitVector.fromHex("abcd").get, isLeaf = false) shouldBe hex"00abcd"
      hp.encode(BitVector.fromHex("abcd").get, isLeaf = true) shouldBe hex"20abcd"
      hp.encode(BitVector.fromHex("9abcd").get, isLeaf = false) shouldBe hex"19abcd"
      hp.encode(BitVector.fromHex("9abcd").get, isLeaf = true) shouldBe hex"39abcd"

      hp.decode(hex"00abcd") shouldBe (false, BitVector.fromHex("abcd").get)
      hp.decode(hex"20abcd") shouldBe (true, BitVector.fromHex("abcd").get)
      hp.decode(hex"19abcd") shouldBe (false, BitVector.fromHex("9abcd").get)
      hp.decode(hex"39abcd") shouldBe (true, BitVector.fromHex("9abcd").get)

      forAll(hexGen, boolGen) {
        case (hex, isLeaf) =>
          val nibbles = hp.encode(BitVector.fromHex(hex).get, isLeaf = isLeaf)
          val decoded = hp.decode(nibbles)
          decoded shouldBe (isLeaf, BitVector.fromHex(hex).get)
      }
    }
  }
}
