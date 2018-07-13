package jbok.codec

import jbok.codec.rlp._
import scodec.bits._
import scodec.codecs._

//class RlpSpec extends JbokSpec {
//  "rlp codec" should {
//    "codec item length" in {
//      // single value
//      for (b <- 0x00 to 0x7f) {
//        val bits = BitVector(b)
//        itemLen.encode(Left(b)).require shouldBe bits
//        itemLen.decode(bits).require.value shouldBe Left(b)
//      }
//
//      // [0, 55] bytes item
//      for (b <- 0x80 to 0xb7) {
//        val bits = BitVector(b)
//        itemLen.encode(Right(b - 0x80)).require shouldBe bits
//        itemLen.decode(bits).require.value shouldBe Right(b - 0x80)
//      }
//
//      // more than 55 bytes
//      // we can't use 0xbf because we have no unsigned long
//      for (b <- 0xb8 to 0xbf - 1) {
//        val l = b - 0xb7
//        val bytes = ByteVector(List.fill(l)(0xff): _*)
//        val bits = (ByteVector(b) ++ bytes).bits
//        val length = Right(ulong(l * 8).decode(bytes.bits).require.value)
//
//        itemLen.encode(length).require shouldBe bits
//        itemLen.decode(bits).require.value shouldBe length
//      }
//    }
//
//    "codec list length" in {
//      // [0, 55] bytes list
//      for (b <- 0xc0 to 0xf7) {
//        val bits = BitVector(b)
//
//        listLen.encode(b - 0xc0).require shouldBe bits
//        listLen.decode(bits).require.value shouldBe b - 0xc0
//      }
//
//      for (b <- 0xf8 to 0xff - 1) {
//        val l = b - 0xf7
//        val bytes = ByteVector(List.fill(l)(0xff): _*)
//        val bits = (ByteVector(b) ++ bytes).bits
//        val length = ulong(l * 8).decode(bytes.bits).require.value
//
//        listLen.encode(length).require shouldBe bits
//        listLen.decode(bits).require.value shouldBe length
//      }
//    }
//
//    def codec[A](a: A)(implicit codec: RlpCodec[A]): ByteVector = {
//      val encoded = rlp.encode[A](a)
//      val decoded = encoded.flatMap(bits => rlp.decode[A](bits))
//      a shouldBe decoded.require.value
//      encoded.require.bytes
//    }
//
//    "codec case class" in {
//      case class Foo(
//          a: Int,
//          b: String,
//          c: List[Int]
//      )
//
//      implicit val fooCodec: RlpCodec[Foo] =
//        rstruct((ritem(uint16) :: rstring :: rlist(ritem(uint16))).as[Foo])
//
//      val foo = Foo(1024, "bar", List(1, 2, 3))
//      codec(foo)
//    }
//
//    "encode and decode" in {
//      codec("dog")(rstring) shouldBe hex"0x83646f67"
//      codec("")(rstring) shouldBe hex"0x80"
//      codec("Lorem ipsum dolor sit amet, consectetur adipisicingelit")(ritem(string))
//      codec(List("cat", "dog"))(rlist(rstring))
//      codec(List.empty[String])(rlist(rstring)) shouldBe hex"c0"
//      codec(0)(ruint8) shouldBe hex"0x00"
//      codec(15)(ruint8) shouldBe hex"0x0f"
//      codec(1024)(ritem(uint16)) shouldBe hex"0x820400"
//    }
//  }
//}
