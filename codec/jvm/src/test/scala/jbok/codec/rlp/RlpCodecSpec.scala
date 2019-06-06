package jbok.codec.rlp

import cats.effect.IO
import jbok.codec.rlp.implicits._
import jbok.codec.testkit._
import jbok.common.CommonSpec
import scodec.bits._
import spire.math.SafeLong

class RlpCodecSpec extends CommonSpec {
  "RlpCodec" should {
    "constants" in {
      RlpEncoded.emptyItem shouldBe RlpEncoded.coerce(hex"0x80".bits)
      RlpEncoded.emptyList shouldBe RlpEncoded.coerce(hex"0xc0".bits)
    }

    "codec boolean" in {
      roundtripAndMatch(false, hex"00")
      roundtripAndMatch(true, hex"01")
    }

    """codec 0/()/"" as 0x80""" in {
      roundtripAndMatch(0, hex"0x80")
      roundtripAndMatch("", hex"0x80")
      roundtripAndMatch((), hex"0x80")
    }

    "codec scalar" in {
      0.encoded shouldBe RlpEncoded.emptyItem
      0L.encoded shouldBe RlpEncoded.emptyItem
      BigInt(0).encoded shouldBe RlpEncoded.emptyItem
      SafeLong(0).encoded shouldBe RlpEncoded.emptyItem

      roundtripAndMatch(0, hex"0x80")
      roundtripAndMatch(0L, hex"0x80")
      roundtripAndMatch(BigInt(0), hex"0x80")
      roundtripAndMatch(SafeLong(0), hex"0x80")

      roundtrip(Int.MaxValue)
      roundtrip(Long.MaxValue)

      roundtripAndMatch(
        BigInt("115792089237316195423570985008687907853269984665640564039457584007913129639935"),
        hex"0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
      )
      roundtripAndMatch(
        SafeLong(BigInt("115792089237316195423570985008687907853269984665640564039457584007913129639935")),
        hex"0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
      )
    }

    "codec scalar to the same value" in {
      forAll { l: Long =>
        if (l >= 0) {
          if (l.isValidInt) {
            val i = l.toInt
            i.encoded shouldBe l.encoded
            i.encoded shouldBe BigInt(i).encoded
            roundtrip(i)
          } else {
            l.encoded shouldBe BigInt(l).encoded
            roundtrip(l)
          }
        } else {
          IO(l.encoded).attempt.unsafeRunSync().isLeft shouldBe true
          IO(BigInt(l).encoded).attempt.unsafeRunSync().isLeft shouldBe true
        }
      }
    }

    "codec option as value or null" in {
//      RlpCodec[Option[Int]].prefixType shouldBe PrefixType.ItemLenPrefix
    }

    "codec RlpEncoded as identity" in {
      forAll { bytes: ByteVector =>
        val encoded = bytes.encoded
        encoded.encoded shouldBe encoded
        encoded.encoded.encoded shouldBe encoded
        encoded.decoded[RlpEncoded] shouldBe Right(encoded)
      }
    }

    "codec List[RlpEncoded]" in {
      val a = hex"dead".encoded
      val b = hex"beef".encoded
      roundtripAndMatch(List(a, b), hex"0xc6" ++ a.bits.bytes ++ b.bits.bytes)

      val c = List(a, b).encoded
      roundtripAndMatch(List(a, b, c), hex"0xcd" ++ a.bits.bytes ++ b.bits.bytes ++ c.bits.bytes)
    }

    "codec List[A]" in {
      List.empty[Int].encoded shouldBe RlpEncoded.emptyList
      List.empty[String].encoded shouldBe RlpEncoded.emptyList
      roundtripAndMatch(List(1, 2, 3), hex"0xc3010203")
    }

    "derive product(tuple) codec" in {
      roundtripAndMatch((1, "abc"), hex"0xc50183616263")
      roundtripAndMatch((1, 1024 * 1024, "abc"), hex"0xc9018310000083616263")
    }

    "derive product(case class) codec as hlist" in {
      case class Foo(a: Int, b: Int, c: String)
      roundtripAndMatch(Foo(42, 1024 * 1024, "abc"), hex"0xc92a8310000083616263")
    }

    "derive coproduct(ADT) codec with one byte discriminator" in {
      sealed trait Tree
      case object Leaf          extends Tree
      case class Branch(v: Int) extends Tree

      roundtripAndMatch(Leaf.asInstanceOf[Tree], hex"0x8200c0")
      roundtripAndMatch(Branch(42).asInstanceOf[Tree], hex"0x8301c12a")
    }

    "derive value class as its value codec" in {
      import RlpCodecSpec._
      roundtripAndMatch(WrappedInt(42), hex"0x2a")
      roundtripAndMatch(WrappedString("abc"), hex"0x83616263")
    }

    "codec https://github.com/ethereum/wiki/wiki/RLP" in {
      roundtripAndMatch(0, hex"0x80")
      roundtripAndMatch("", hex"0x80")
      roundtripAndMatch("d", hex"0x64")
      roundtripAndMatch("cat", hex"0x83636174")
      roundtripAndMatch("dog", hex"0x83646f67")
      roundtripAndMatch(List("cat", "dog"), hex"0xc88363617483646f67")
      roundtripAndMatch(List("dog", "god", "cat"), hex"0xcc83646f6783676f6483636174")
      roundtripAndMatch(1, hex"0x01")
      roundtripAndMatch(10, hex"0x0a")
      roundtripAndMatch(100, hex"0x64")
      roundtripAndMatch(1000, hex"0x8203e8")
      roundtripAndMatch(
        SafeLong(BigInt("115792089237316195423570985008687907853269984665640564039457584007913129639935")),
        hex"0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
      )
      roundtripAndMatch(
        SafeLong(BigInt("115792089237316195423570985008687907853269984665640564039457584007913129639936")),
        hex"0xa1010000000000000000000000000000000000000000000000000000000000000000"
      )
    }
  }
}

object RlpCodecSpec {
  case class WrappedInt(i: Int)       extends AnyVal
  case class WrappedString(s: String) extends AnyVal
}
