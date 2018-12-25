package jbok.codec

import java.util.UUID

import cats.effect.Sync
import scodec._
import scodec.bits._

package object rlp {
  trait codecs {
    val ubigint: Codec[BigInt] = new Codec[BigInt] {
      val codec = codecs.bytes.xmap[BigInt](
        bytes => {
          if (bytes.isEmpty) 0 else BigInt(1, bytes.toArray)
        },
        bi => {
          require(bi >= 0, "unsigned codec cannot encode negative values")
          val bytes = bi.toByteArray
          ByteVector(if (bytes.head == 0) bytes.tail else bytes)
        }
      )

      override def encode(value: BigInt): Attempt[BitVector]              = codec.encode(value)
      override def decode(bits: BitVector): Attempt[DecodeResult[BigInt]] = codec.decode(bits)
      override def sizeBound: SizeBound                                   = SizeBound.atLeast(1L)
      override def toString: String                                       = "UBigInt"
    }

    val ulong: Codec[Long] = new Codec[Long] {
      val codec = ubigint.xmap[Long](_.toLong, BigInt.apply)

      override def encode(value: Long): Attempt[BitVector]              = codec.encode(value)
      override def decode(bits: BitVector): Attempt[DecodeResult[Long]] = codec.decode(bits)
      override def sizeBound: SizeBound                                 = SizeBound.bounded(1L, 8L)
      override def toString: String                                     = "ULong"
    }

    val uint: Codec[Int] = new Codec[Int] {
      val codec = ubigint.xmap[Int](_.toInt, BigInt.apply)

      override def encode(value: Int): Attempt[BitVector]              = codec.encode(value)
      override def decode(bits: BitVector): Attempt[DecodeResult[Int]] = codec.decode(bits)
      override def sizeBound: SizeBound                                = SizeBound.bounded(1L, 4L)
      override def toString: String                                    = "UInt"
    }

    val bool: Codec[Boolean] = new Codec[Boolean] {
      val fb    = hex"00"
      val tb    = hex"01"
      val codec = codecs.bytes(1).xmap[Boolean](bytes => if (bytes == fb) false else true, b => if (b) tb else fb)

      override def encode(value: Boolean): Attempt[BitVector]              = codec.encode(value)
      override def decode(bits: BitVector): Attempt[DecodeResult[Boolean]] = codec.decode(bits)
      override def sizeBound: SizeBound                                    = SizeBound.exact(1L)
      override def toString: String                                        = "Boolean"
    }

    def nop[A](implicit codec: Codec[A]): RlpCodec[A] =
      RlpCodec[A](PrefixType.NoPrefix, codec)

    def rlp[A](implicit codec: Codec[A]): RlpCodec[A] =
      RlpCodec[A](PrefixType.ItemLenPrefix, codec)

    def rlplist[A](implicit codec: Codec[A]): RlpCodec[A] =
      RlpCodec[A](PrefixType.ListLenPrefix, codec)
  }

  trait CodecSyntax {
    implicit def implicitCodecOps[A: RlpCodec](a: A): CodecOps[A] = new CodecOps[A](a)
  }

  final class CodecOps[A](val a: A) extends AnyVal {
    def asBits(implicit codec: RlpCodec[A]): BitVector                   = codec.encode(a).require
    def asBytes(implicit codec: RlpCodec[A]): ByteVector                 = asBits.bytes
    def asBitsF[F[_]: Sync](implicit codec: RlpCodec[A]): F[BitVector]   = Sync[F].delay(asBits)
    def asBytesF[F[_]: Sync](implicit codec: RlpCodec[A]): F[ByteVector] = Sync[F].delay(asBytes)
  }

  object implicits extends codecs with CodecSyntax {
    type RlpCodec[A] = jbok.codec.rlp.RlpCodec[A]

    val RlpCodec = jbok.codec.rlp.RlpCodec

    implicit val rlpUnitCodec: RlpCodec[Unit] = rlp(codecs.constant(ByteVector.empty))

    implicit val rlpUBigintCodec: RlpCodec[BigInt] = rlp(ubigint)

    implicit val rlpULongCodec: RlpCodec[Long] = rlp(ulong)

    implicit val rlpUIntCodec: RlpCodec[Int] = rlp(uint)

    implicit val rlpBoolCodec: RlpCodec[Boolean] = rlp(bool)

    implicit val rlpUtf8Codec: RlpCodec[String] = rlp(codecs.utf8)

    implicit val rlpBytesCodec: RlpCodec[ByteVector] = rlp(codecs.bytes)

    implicit val rlpUuidCodec: RlpCodec[UUID] = rlp(codecs.uuid)

    implicit def rlpOptionalCodec[A](implicit codec: RlpCodec[A]): RlpCodec[Option[A]] =
      rlp[Option[A]](codecs.optional(bool, codec.valueCodec))

    implicit def rlpEitherCodec[L, R](implicit cl: RlpCodec[L], cr: RlpCodec[R]): RlpCodec[Either[L, R]] =
      rlp[Either[L, R]](codecs.either[L, R](bool, cl.valueCodec, cr.valueCodec))

    implicit def rlpListCodec[A](implicit codec: RlpCodec[A]): RlpCodec[List[A]] =
      rlplist[List[A]](codecs.list(codec))
  }
}
