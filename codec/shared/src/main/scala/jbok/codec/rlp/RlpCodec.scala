package jbok.codec.rlp

import scodec.Attempt.{Failure, Successful}
import scodec._
import scodec.codecs._
import scodec.bits._
import shapeless._

import scala.annotation.tailrec

sealed trait CodecType
case object PureCodec extends CodecType
case object ItemCodec extends CodecType
case object HListCodec extends CodecType

final case class RlpCodec[A](codecType: CodecType, valueCodec: Codec[A]) {
  import RlpCodec.listLengthCodec

  val codec: Codec[A] = codecType match {
    case ItemCodec  => RlpCodec.rlpitem(valueCodec)
    case PureCodec  => valueCodec
    case HListCodec => variableSizeBytes(listLengthCodec.xmap[Int](_.toInt, _.toLong), valueCodec)
  }

  def xmap[B](f: A => B, g: B => A): RlpCodec[B] = RlpCodec(codecType, valueCodec.xmap(f, g))

  def encode(a: A): Attempt[BitVector] = codec.encode(a)

  def decode(bits: BitVector): Attempt[DecodeResult[A]] = codec.decode(bits)
}

object RlpCodec {
  def apply[A](implicit codec: Lazy[RlpCodec[A]]): RlpCodec[A] = codec.value

  def encode[A](a: A)(implicit codec: RlpCodec[A]) =
    codec.encode(a)

  def decode[A](bits: BitVector)(implicit codec: RlpCodec[A]) =
    codec.decode(bits)

  def pure[A](codec: Codec[A]): RlpCodec[A] = RlpCodec(PureCodec, codec)

  def apply[A](encode: A => Attempt[BitVector], decode: BitVector => Attempt[DecodeResult[A]]): RlpCodec[A] =
    pure(Codec(encode, decode))

  def item[A](codec: Codec[A]): RlpCodec[A] =
    RlpCodec(ItemCodec, codec)

  //////////////////////////
  //////////////////////////

  private[jbok] val itemOffset = 0x80

  private[jbok] val itemLengthCodec: Codec[Either[Int, Long]] = lengthCodec(itemOffset)

  private[jbok] val listOffset = 0xc0

  private[jbok] val listLengthCodec: Codec[Long] = lengthCodec(listOffset).narrow[Long]({
    case Left(l)  => Failure(Err(s"invalid rlp list length $l"))
    case Right(l) => Successful(l)
  }, Right.apply)

  /**
    * A string(i.e. ByteVector) is an item
    * For a **single** byte whose value is in the [0x00, 0x7f] range, that byte is its own RLP encoding.
    */
  private[jbok] val rlpitem: Codec[ByteVector] = itemLengthCodec.consume[ByteVector] {
    case Left(v)  => provide(ByteVector(v)) // provide literal
    case Right(l) => bytes(l.toInt) // read next l bytes
  } { b =>
    if (b.length == 1 && b(0) >= 0x00 && b(0) <= 0x7f) Left(b(0).toInt) // [0x00, 0x7f]
    else Right(b.length) // how many bytes to read
  }

  private[jbok] def rlplist[A](codec: RlpCodec[A]): RlpCodec[List[A]] =
    pure(variableSizeBytes(listLengthCodec.xmap[Int](_.toInt, _.toLong), list(codec.codec)))

  private[jbok] def rlpset[A](codec: RlpCodec[A]): RlpCodec[Set[A]] =
    pure(
      variableSizeBytes(listLengthCodec.xmap[Int](_.toInt, _.toLong), list(codec.codec).xmap[Set[A]](_.toSet, _.toList))
    )

//  private[jbok] def rlpIsoList[A](codec: Codec[A]): Codec[List[A]] =
//    variableSizeBytes(listLengthCodec.xmap[Int](_.toInt, _.toLong), list(rlpitem(codec)))

//  private[jbok] def rlpHeteroList[A <: HList](codec: Codec[A]): Codec[A] =
//    variableSizeBytes(listLengthCodec.xmap[Int](_.toInt, _.toLong), codec)

  /**
    * lift a normal codec to rlp codec
    * @param codec to encode/decode A
    * @tparam A type
    * @return RlpCodec[A]
    */
  private[jbok] def rlpitem[A](codec: Codec[A]): Codec[A] =
    Codec(
      { a: A =>
        codec
          .encode(a)
          .flatMap(bits => rlpitem.encode(bits.bytes))
      }, { bits =>
        for {
          result1 <- rlpitem.decode(bits)
          result2 <- codec.decode(result1.value.bits)
        } yield result2.mapRemainder(_ => result1.remainder)
      }
    )

  /**
    * @param offset item offset 0x80 or list offset 0xc0
    * @return Left(value) if this is a single byte in [0x00, 0x7f], otherwise Right(length)
    */
  private[jbok] def lengthCodec(offset: Int): Codec[Either[Int, Long]] =
    uint8.consume[Either[Int, Long]] { h =>
      if (h < offset) provide(Left(h))
      else if ((h - offset) <= 55) provide(Right((h - offset).toLong))
      else ulong((h - offset - 55) * 8).xmap(Right.apply, _.right.get)
    } {
      case Left(l)             => l
      case Right(l) if l <= 55 => l.toInt + offset
      case Right(l)            => binaryLength(l) + offset + 55
    }

  /**
    * @param value length
    * @return how many bytes to represent the length
    */
  private[jbok] def binaryLength(value: Long): Int = {
    @tailrec
    def bl0(value: Long, len: Int): Int =
      if (len >= 8 || value < (1L << (len * 8)))
        len
      else
        bl0(value, len + 1)

    bl0(value, 1)
  }
}
