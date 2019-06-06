package jbok.codec.rlp

import java.net.URI
import java.util.UUID

import cats.implicits._
import scodec._
import scodec.bits.{BitVector, ByteVector}
import spire.math.SafeLong

import scala.concurrent.duration.Duration

trait RlpCodecInstances extends LowerPriorityRlpCodec {
  import BasicCodecs._

  def nop[A](codec: Codec[A]): RlpCodec[A] =
    RlpCodecHelper.fromCodec(PrefixType.NoPrefix, codec)

  def rlp[A](codec: Codec[A]): RlpCodec[A] =
    RlpCodecHelper.fromCodec(PrefixType.ItemLenPrefix, codec)

  def rlplist[A](codec: Codec[A]): RlpCodec[A] =
    RlpCodecHelper.fromCodec(PrefixType.ListLenPrefix, codec)

  implicit val rlpCodec: RlpCodec[RlpEncoded] = new RlpCodec[RlpEncoded] {
    import RlpCodecHelper._

    def decodeItem(bits: BitVector): Attempt[DecodeResult[RlpEncoded]] =
      lengthCodec(itemOffset).decode(bits).flatMap { result =>
        result.value match {
          case Left(v) => Attempt.successful(DecodeResult(RlpEncoded.coerce(BitVector(v)), result.remainder)) // we are decoding literal in [0x00, 0x7f]
          case Right(l) =>
            for {
              result <- codecs.bits(l * 8).decode(result.remainder)                    // read next l * 8 bits
              prefix <- lengthCodec(itemOffset).encode(Right(result.value.length / 8)) // read back prefix
            } yield result.map(bits => RlpEncoded.coerce(prefix ++ bits))
        }
      }

    def decodeList(bits: BitVector): Attempt[DecodeResult[RlpEncoded]] =
      lengthCodec(listOffset).decode(bits).flatMap { result =>
        result.value match {
          case Left(_) => Attempt.failure(Err(s"invalid rlp list prefix: ${bits.bytes}"))
          case Right(l) =>
            for {
              result <- codecs.bits(l * 8).decode(result.remainder)
              prefix <- lengthCodec(listOffset).encode(Right(result.value.length / 8))
            } yield result.map(bits => RlpEncoded.coerce(prefix ++ bits))
        }
      }

    override def encode(value: RlpEncoded): Attempt[BitVector] =
      Attempt.successful(value.bits)

    override def decode(bits: BitVector): Attempt[DecodeResult[RlpEncoded]] =
      if (RlpCodec.isItemPrefix(bits)) {
        decodeItem(bits)
      } else {
        decodeList(bits)
      }
  }

  implicit val rlpByteVectorCodec: RlpCodec[ByteVector] = rlp(codecs.bytes)

  implicit val rlpByteArrayCodec: RlpCodec[Array[Byte]] = rlp(codecs.bytes.xmap[Array[Byte]](_.toArray, ByteVector.apply))

  implicit val rlpUnitCodec: RlpCodec[Unit] = rlp(codecs.constant(ByteVector.empty))

  implicit val rlpUBigintCodec: RlpCodec[BigInt] = rlp(ubigint)

  implicit val rlpULongCodec: RlpCodec[Long] = rlp(ulong)

  implicit val rlpUIntCodec: RlpCodec[Int] = rlp(uint)

  implicit val rlpUSafeLong: RlpCodec[SafeLong] = rlp(uSafeLong)

  implicit val rlpBoolCodec: RlpCodec[Boolean] = rlp(bool)

  implicit val rlpUtf8Codec: RlpCodec[String] = rlp(codecs.utf8)

  implicit val rlpUuidCodec: RlpCodec[UUID] = rlp(codecs.uuid)

  implicit val rlpDurationCodec: RlpCodec[Duration] = rlp(duration)

  implicit val rlpUriCodec: RlpCodec[URI] = rlp(uri)

  implicit def rlpListCodec[A](implicit codec: RlpCodec[A]): RlpCodec[List[A]] =
    rlplist(codecs.list(codec))

  def optionAsNull[A](codec: RlpCodec[A]): RlpCodec[Option[A]] = new RlpCodec[Option[A]] {
    override def encode(value: Option[A]): Attempt[BitVector] = value match {
      case Some(a) => codec.encode(a)
      case None    => Attempt.successful(RlpEncoded.emptyItem.bits)
    }

    override def decode(bits: BitVector): Attempt[DecodeResult[Option[A]]] =
      if (RlpCodec.isEmptyPrefix(bits)) {
        Attempt.successful(DecodeResult(None, bits.drop(8)))
      } else {
        codec.decode(bits).map(_.map(_.some))
      }
  }
}
