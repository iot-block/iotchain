package jbok.codec

import jbok.JbokSpec
import jbok.codec.rlp._
import scodec.bits._
import scodec.codecs._

class RlpSpec extends JbokSpec {
  def codec[A](a: A)(implicit codec: RlpCodec[A]): ByteVector = {
    val encoded = rlp.encode[A](a)
    val decoded = encoded.flatMap(bits => rlp.decode[A](bits))
    a shouldBe decoded.require.value
    encoded.require.toByteVector
  }

  "rlp codec" should {
    "codec case class" in {
      case class Foo(
          a: Int,
          b: String,
          c: List[Int],
          d: Option[Int]
      )

      implicit val fooCodec: RlpCodec[Foo] =
        (uint16 :: rlpString :: rlpList(uint16) :: optional(bool, uint16)).as[Foo]

      val foo = Foo(1024, "bar", List(1, 2, 3), Some(42))
      codec(foo)
    }

    "encode and decode" in {
      codec("dog")
      codec("")
      codec("Lorem ipsum dolor sit amet, consectetur adipisicing elit")
      codec(List("cat", "dog"))
      codec(List.empty[String]) shouldBe hex"c0"
      codec(0)(uint8)
      codec(15)(uint8)
      codec(1024)(uint16)
    }

    "codec single byte" in {
      for (b <- 0x00 to 0x7f) {
        codec(BitVector(b).toByteVector)
      }
    }

    "codec single array" in {
      for (b <- 0x80 to 0xff) {
        val bits = BitVector(0x80 + 1, b)
        codec(bits.toByteVector)
      }
    }

    "codec empty array" in {
      codec(BitVector(0x80).toByteVector)
    }
  }
}
