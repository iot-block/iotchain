package jbok.codec.rlp

import java.math.BigInteger
import java.nio.charset.StandardCharsets

import jbok.codec.rlp.RlpCodec.item
import scodec.{Attempt, Codec, DecodeResult, SizeBound}
import scodec.bits.{BitVector, ByteVector}
import scodec.codecs._
import shapeless._

object codecs {
  implicit def deriveList[A](implicit codec: Lazy[RlpCodec[A]]): RlpCodec[List[A]] =
    RlpCodec.rlplist(codec.value)

  implicit def deriveSet[A](implicit codec: Lazy[RlpCodec[A]]): RlpCodec[Set[A]] =
    RlpCodec.rlpset(codec.value)

  implicit def deriveMap[A, B](implicit codec: Lazy[RlpCodec[List[(A, B)]]]): RlpCodec[Map[A, B]] =
    codec.value.xmap[Map[A, B]](_.toMap, _.toList)

  implicit val hnilCodec: RlpCodec[HNil] = RlpCodec(
    PureCodec,
    new Codec[HNil] {
      override def encode(value: HNil): Attempt[BitVector]              = Attempt.successful(BitVector.empty)
      override def sizeBound: SizeBound                                 = SizeBound.exact(0)
      override def decode(bits: BitVector): Attempt[DecodeResult[HNil]] = Attempt.successful(DecodeResult(HNil, bits))
      override def toString                                             = s"HNil"
    }
  )

  implicit final class HListSupport[L <: HList](val self: RlpCodec[L]) extends AnyVal {
    def ::[B](codec: RlpCodec[B]): RlpCodec[B :: L] = prepend(codec, self)

    def prepend[H, T <: HList](h: RlpCodec[H], t: RlpCodec[T]): RlpCodec[H :: T] = t.codecType match {
      case PureCodec | ItemCodec => RlpCodec(HListCodec, h.codec :: t.codec)
      case HListCodec            => RlpCodec(HListCodec, h.codec :: t.valueCodec)
    }
  }

  implicit final class HListSupportSingleton[A](val self: RlpCodec[A]) extends AnyVal {
    def ::[B](codecB: RlpCodec[B]): RlpCodec[B :: A :: HNil] =
      codecB :: self :: hnilCodec
  }

  implicit def deriveHList[A, L <: HList](implicit a: Lazy[RlpCodec[A]], l: RlpCodec[L]): RlpCodec[A :: L] =
    a.value :: l

  implicit def deriveGeneric[A, R <: HList](implicit gen: Generic.Aux[A, R], codec: Lazy[RlpCodec[R]]): RlpCodec[A] =
    RlpCodec(a => codec.value.encode(gen.to(a)), bits => codec.value.decode(bits).map(_.map(r => gen.from(r))))

  implicit val rempty: RlpCodec[Unit] = RlpCodec.pure(constant(RlpCodec.itemOffset))

  implicit val rbyte: RlpCodec[Byte] = item(byte)

  implicit val rbytes: RlpCodec[ByteVector] = item(bytes)

  implicit val rbyteslist: RlpCodec[List[ByteVector]] = deriveList(rbytes)

  implicit val rbytearray: RlpCodec[Array[Byte]] = item(bytes.xmap[Array[Byte]](_.toArray, arr => ByteVector(arr)))

  implicit val rstring: RlpCodec[String] = item(string(StandardCharsets.UTF_8))

  implicit val rvint: RlpCodec[Int] = item(
    bytes.xmap[Int](
      bytes =>
        bytes.length match {
          case 0 => 0
          case 1 => bytes(0) & 0xFF
          case 2 => ((bytes(0) & 0xFF) << 8) + (bytes(1) & 0xFF)
          case 3 => ((bytes(0) & 0xFF) << 16) + ((bytes(1) & 0xFF) << 8) + (bytes(2) & 0xFF)
          case Integer.BYTES =>
            ((bytes(0) & 0xFF) << 24) + ((bytes(1) & 0xFF) << 16) + ((bytes(2) & 0xFF) << 8) + (bytes(3) & 0xFF)
          case _ => throw new RuntimeException("bytes it not an int")
      },
      i => {
        if (i == (i & 0xFF)) byteToBytes(i.toByte)
        else if (i == (i & 0xFFFF)) shortToBytes(i.toShort)
        else if (i == (i & 0xFFFFFF)) ByteVector((i >>> 16).toByte, (i >>> 8).toByte, i.toByte)
        else ByteVector((i >>> 24).toByte, (i >>> 16).toByte, (i >>> 8).toByte, i.toByte)
      }
    ))

  implicit val rvshort: RlpCodec[Short] =
    item(
      bytes.xmap[Short](
        bytes =>
          bytes.length match {
            case 0 => 0
            case 1 => (bytes(0) & 0xFF).toShort
            case 2 => (((bytes(0) & 0xFF) << 8) + (bytes(1) & 0xFF)).toShort
            case _ => throw new RuntimeException("bytes is not a short")
        },
        short => shortToBytes(short)
      ))

  implicit val rubigint: RlpCodec[BigInt] =
    item(bytes.xmap[BigInt](bytes => if (bytes.isEmpty) 0 else BigInt(1, bytes.toArray), bi => {
      val bytes = bi.toByteArray
      ByteVector(if (bytes.head == 0) bytes.tail else bytes)
    }))

  implicit val rubiginteger: RlpCodec[BigInteger] = rubigint.xmap[BigInteger](_.underlying(), bi => BigInt(bi))

  implicit val rulong: RlpCodec[Long] = rubigint.xmap[Long](_.toLong, BigInt.apply)

  implicit val rbool: RlpCodec[Boolean] = item(bool(8))

  implicit def roptional[A](implicit c: Lazy[RlpCodec[A]]): RlpCodec[Option[A]] =
    item(optional(bool(8), c.value.valueCodec))

  implicit def reither[L, R](implicit cl: Lazy[RlpCodec[L]], cr: Lazy[RlpCodec[R]]):RlpCodec[Either[L, R]] =
    item(either[L, R](bool(8), cl.value.valueCodec, cr.value.valueCodec))

  private def byteToBytes(byte: Byte): ByteVector =
    if ((byte & 0xFF) == 0) ByteVector.empty
    else ByteVector(byte)

  private def shortToBytes(short: Short): ByteVector =
    if ((short & 0xFF) == short) byteToBytes(short.toByte)
    else ByteVector((short >> 8 & 0xFF).toByte, (short >> 0 & 0xFF).toByte)

}
