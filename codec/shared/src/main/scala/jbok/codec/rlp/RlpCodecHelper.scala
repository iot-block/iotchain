package jbok.codec.rlp

import scodec.Attempt.{Failure, Successful}
import scodec._
import scodec.bits.{BitVector, ByteVector}

import scala.annotation.tailrec

sealed trait PrefixType
object PrefixType {
  case object NoPrefix      extends PrefixType
  case object ItemLenPrefix extends PrefixType
  case object ListLenPrefix extends PrefixType
}

object RlpCodecHelper {
  val itemOffset: Int = 0x80

  val listOffset: Int = 0xc0

  private[rlp] def binaryLength(value: Long): Int = {
    @tailrec
    def bl0(value: Long, len: Int): Int =
      if (len >= 8 || value < (1L << (len * 8)))
        len
      else
        bl0(value, len + 1)

    bl0(value, 1)
  }

  private[rlp] def lengthCodec(offset: Int): Codec[Either[Int, Long]] = new Codec[Either[Int, Long]] {
    val codec: Codec[Either[Int, Long]] = codecs.uint8.consume[Either[Int, Long]] { h =>
      if (h < offset) codecs.provide(Left(h))
      else if ((h - offset) <= 55) codecs.provide(Right((h - offset).toLong))
      else codecs.ulong((h - offset - 55) * 8).xmap(Right.apply, _.fold(_.toLong, identity))
    } {
      case Left(l)             => l
      case Right(l) if l <= 55 => l.toInt + offset
      case Right(l)            => binaryLength(l) + offset + 55
    }

    override def encode(value: Either[Int, Long]): Attempt[BitVector]              = codec.encode(value)
    override def decode(bits: BitVector): Attempt[DecodeResult[Either[Int, Long]]] = codec.decode(bits)
    override def sizeBound: SizeBound                                              = SizeBound.atLeast(1L)
  }

  private[rlp] val itemPrefixCodec: Codec[ByteVector] = lengthCodec(itemOffset).consume[ByteVector] {
    case Left(v)  => codecs.provide(ByteVector.apply(v)) // provide literal
    case Right(l) => codecs.bytes(l.toInt)               // read next l bytes
  } { b =>
    if (b.length == 1 && b(0) >= 0x00 && b(0) <= 0x7f) Left(b(0).toInt) // [0x00, 0x7f]
    else Right(b.length)                                                // how many bytes to read
  }

  val listLengthCodec: Codec[Long] = lengthCodec(listOffset)
    .narrow[Long]({
      case Left(l)  => Failure(Err(s"invalid rlp list length $l"))
      case Right(l) => Successful(l)
    }, Right.apply)

  private val listPrefixCodec: Codec[ByteVector] =
    listLengthCodec.consume[ByteVector](size => codecs.bytes(size.toInt))(_.length)

  private def prefix[A](lengthCodec: Codec[ByteVector], codec: Codec[A]): Codec[A] =
    Codec[A](
      { a: A =>
        for {
          bits           <- codec.encode(a)
          lengthPrefixed <- lengthCodec.encode(bits.bytes)
        } yield lengthPrefixed
      }, { bits: BitVector =>
        for {
          result <- lengthCodec.decode(bits)
          a      <- codec.decode(result.value.bits)
        } yield a.mapRemainder(_ => result.remainder)
      }
    )

  def fromCodec[A](prefixType: PrefixType, codec: Codec[A]): RlpCodec[A] = new RlpCodec[A] {
    val prefixedCodec: Codec[A] = prefixType match {
      case PrefixType.NoPrefix      => codec
      case PrefixType.ItemLenPrefix => prefix(itemPrefixCodec, codec)
      case PrefixType.ListLenPrefix => prefix(listPrefixCodec, codec)
    }

    override def decode(bits: BitVector): Attempt[DecodeResult[A]] =
      prefixedCodec.decode(bits)

    override def encode(value: A): Attempt[BitVector] =
      prefixedCodec.encode(value)

    override def sizeBound: SizeBound = SizeBound.unknown

    override def toString: String =
      s"RlpCodec(${prefixType}, ${codec})"
  }

  def fromListCodec[A](codec: RlpCodec[A]): RlpCodec[List[A]] = new RlpCodec[List[A]] {
    val listCodec: Codec[List[A]] = prefix(listPrefixCodec, codecs.list(codec))

    override def decode(bits: BitVector): Attempt[DecodeResult[List[A]]] =
      listCodec.decode(bits)

    override def encode(value: List[A]): Attempt[BitVector] =
      listCodec.encode(value)

    override def sizeBound: SizeBound = SizeBound.unknown
  }
}
