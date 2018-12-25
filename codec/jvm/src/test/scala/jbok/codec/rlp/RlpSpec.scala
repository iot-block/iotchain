package jbok.codec.rlp

import cats.effect.IO
import jbok.JbokSpec
import jbok.codec.rlp.implicits._
import jbok.codec.testkit
import scodec.bits._

class RlpSpec extends JbokSpec with testkit {

  "RLP Codec" should {
    "encode boolean" in {
      false.asBytes shouldBe hex"0x00"
      true.asBytes shouldBe hex"0x01"

      roundtrip(true)
      roundtrip(false)
    }

    "encode empty bytes" in {
      ().asBytes shouldBe hex"0x80"
      "".asBytes shouldBe hex"0x80"
      roundtrip("")
    }

    "encode scalar" in {
      0.asBytes shouldBe hex"0x80"
      0L.asBytes shouldBe hex"0x80"
      BigInt(0).asBytes shouldBe hex"0x80"
      roundtrip(0)
      roundtrip(0L)
      roundtrip(BigInt(0))

      0.asBytes.length shouldBe 1
      Int.MaxValue.asBytes.length shouldBe 5
      roundtrip(Int.MaxValue)

      0L.asBytes.length shouldBe 1
      Long.MaxValue.asBytes.length shouldBe 9
      roundtrip(Long.MaxValue)

      BigInt(0).asBytes.length shouldBe 1
      BigInt("115792089237316195423570985008687907853269984665640564039457584007913129639935").asBytes.length shouldBe 33
    }

    "encode scalar to the same value" in {
      forAll { l: Long =>
        if (l >= 0) {
          if (l.isValidInt) {
            val i = l.toInt
            i.asBytes shouldBe l.asBytes
            i.asBytes shouldBe BigInt(i).asBytes
            roundtrip(i)
          } else {
            l.asBytes shouldBe BigInt(l).asBytes
            roundtrip(l)
          }
        } else {
          l.asBytesF[IO].attempt.unsafeRunSync().isLeft shouldBe true
          BigInt(l).asBytesF[IO].attempt.unsafeRunSync().isLeft shouldBe true
        }
      }
    }

    "optional codec" in {
      RlpCodec[Option[Int]].prefixType shouldBe PrefixType.ItemLenPrefix
    }

    "either codec" in {
      RlpCodec[Either[Int, String]].prefixType shouldBe PrefixType.ItemLenPrefix
    }

    "list codec" in {
      RlpCodec[List[Int]].prefixType shouldBe PrefixType.ListLenPrefix
      List.empty[Int].asBytes shouldBe hex"0xc0"
      List.empty[String].asBytes shouldBe hex"0xc0"
      roundtripAndMatch(List(1, 2, 3), hex"0xc3010203")
    }

    "derive product(tuple) codec" in {
      (1, "abc").asBytes shouldBe hex"0xc50183616263"
      (1, 1024 * 1024, "abc").asBytes shouldBe hex"0xc9018310000083616263"
    }

    "derive product(case class) codec" in {
      case class Foo(a: Int, b: Int, c: String)
      Foo(42, 1024 * 1024, "abc").asBytes shouldBe hex"0xc92a8310000083616263"
    }

    "derive coproduct(ADT) codec" in {
      sealed trait Tree
      case object Leaf          extends Tree
      case class Branch(v: Int) extends Tree

      Leaf.asBytes shouldBe hex"0xc0"
      Branch(42).asBytes shouldBe hex"0xc12a"

      RlpCodec.encode[Tree](Leaf).require.bytes shouldBe hex"0x8200c0"
      RlpCodec.encode[Tree](Branch(42)).require.bytes shouldBe hex"0x8301c12a"
    }

    "derive value class" in {
      import RlpSpec._
      WrappedInt(42).asBytes shouldBe hex"0x2a"
      WrappedString("abc").asBytes shouldBe hex"0x83616263"
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
        BigInt("115792089237316195423570985008687907853269984665640564039457584007913129639935"),
        hex"0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
      )
      roundtripAndMatch(
        BigInt("115792089237316195423570985008687907853269984665640564039457584007913129639936"),
        hex"0xa1010000000000000000000000000000000000000000000000000000000000000000"
      )
    }
  }
}

object RlpSpec {
  case class WrappedInt(i: Int)       extends AnyVal
  case class WrappedString(s: String) extends AnyVal
}
