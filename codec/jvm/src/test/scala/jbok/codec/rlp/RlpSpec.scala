package jbok.codec.rlp

import jbok.JbokSpec
import jbok.codec.rlp.RlpCodec._
import jbok.codec.rlp.codecs._
import org.scalacheck.{Arbitrary, Gen}
import scodec.bits.ByteVector
import shapeless.HNil

case class Person(name: String, age: Int)
case class Single(name: String)

class RlpSpec extends JbokSpec {
  def roundtrip[A](a: A, hex: String = "")(implicit ev: RlpCodec[A]) = {
    val bytes = encode(a).require.bytes
    if (hex.nonEmpty) {
      bytes shouldBe ByteVector.fromValidHex(hex)
    }
    decode[A](bytes.bits).require.value shouldBe a
  }

  "rlp" should {
    "codec single byte" in {
      roundtrip(0.toByte, "0x00")
      roundtrip(127.toByte, "0x7f")
      forAll(Gen.choose[Byte](Byte.MinValue, Byte.MaxValue)) { byte =>
        roundtrip(byte)
      }
    }

    "codec Short" in {
      roundtrip(30303, "0x82765f")
      roundtrip(20202, "0x824eea")
      forAll(Gen.choose[Short](Short.MinValue, Short.MaxValue)) { short: Short =>
        roundtrip(short)
      }
    }

    "codec String" in {
      val strGen = (n: Int) => Gen.choose(0, n).flatMap(long => Gen.listOfN(long, Gen.alphaChar).map(_.mkString))

      forAll(strGen(10000)) { str: String =>
        roundtrip(str)
      }
    }

    "codec Int" in {
      roundtrip(0, "0x80")
      roundtrip(127, "0x7f")
      roundtrip(Int.MinValue, "0x8480000000")
      roundtrip(Int.MaxValue, "0x847FFFFFFF")
      forAll(Gen.choose[Int](Int.MinValue, Int.MaxValue)) { int: Int =>
        roundtrip(int)
      }
    }

    "codec unsigned Long" in {
      forAll(Gen.choose[Long](0, Long.MaxValue)) { long: Long =>
        roundtrip(long)
      }
    }

    "codec unsigned BigInt" in {
      roundtrip(BigInt(0), "0x80")
      roundtrip(BigInt("100102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f", 16),
                "a0100102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")

      forAll(Arbitrary.arbitrary[BigInt]) { bigInt: BigInt =>
        roundtrip(bigInt.abs)
      }
    }

    "codec ByteVector" in {
      forAll(Gen.nonEmptyListOf(Arbitrary.arbitrary[Byte])) { byteArray =>
        roundtrip(ByteVector(byteArray))
      }
    }

    "codec homogeneous list" in {
      forAll(Gen.nonEmptyListOf(Gen.choose[Long](0, Long.MaxValue))) { ll =>
        roundtrip(ll)
      }
    }

    "codec empty list" in {
      roundtrip(List.empty[Int], "0xc0")
      roundtrip(List.empty[Short], "0xc0")
      roundtrip(List.empty[Byte], "0xc0")
      roundtrip(List.empty[Long], "0xc0")
      roundtrip(List.empty[String], "0xc0")
      roundtrip(List.empty[BigInt], "0xc0")
    }

    "codec long list" in {
      roundtrip(
        List("cat", "Lorem ipsum dolor sit amet, consectetur adipisicing elit"),
        "f83e83636174b8384c6f72656d20697073756d20646f6c6f722073697420616d65742c20636f6e7365637465747572206164697069736963696e6720656c6974"
      )
    }

    "codec hlist" in {
      val hlist = 1 :: List("cat") :: "dog" :: List(2) :: HNil
      roundtrip(hlist, "cc01c48363617483646f67c102")
    }

    "codec https://github.com/ethereum/wiki/wiki/RLP" in {
      val data = Seq(
        encode(0) -> "80",
        encode("") -> "80",
        encode("d") -> "64",
        encode("cat") -> "83636174",
        encode("dog") -> "83646f67",
        encode(List("cat", "dog")) -> "c88363617483646f67",
        encode(List("dog", "god", "cat")) -> "cc83646f6783676f6483636174",
        encode(1) -> "01",
        encode(10) -> "0a",
        encode(100) -> "64",
        encode(1000) -> "8203e8",
        encode(BigInt("115792089237316195423570985008687907853269984665640564039457584007913129639935"))
          -> "a0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
        encode(BigInt("115792089237316195423570985008687907853269984665640564039457584007913129639936"))
          -> "a1010000000000000000000000000000000000000000000000000000000000000000"
      )
      for {
        (attempt, hex) <- data
      } {
        attempt.require.bytes shouldBe ByteVector.fromValidHex(hex)
      }
    }
  }
}
