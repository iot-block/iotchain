package jbok.codec.rlp

import magnolia._
import scodec.Attempt.{Failure, Successful}
import scodec._
import scodec.bits.{BitVector, ByteVector}

import scala.annotation.tailrec
import scala.language.experimental.macros

sealed trait PrefixType
object PrefixType {
  case object NoPrefix      extends PrefixType
  case object ItemLenPrefix extends PrefixType
  case object ListLenPrefix extends PrefixType
}

final case class RlpCodec[A](prefixType: PrefixType, valueCodec: Codec[A]) extends Codec[A] {
  import RlpCodec._

  def prefix(lengthCodec: Codec[ByteVector], codec: Codec[A]): Codec[A] =
    Codec[A](
      { a: A =>
        for {
          bits           <- valueCodec.encode(a)
          lengthPrefixed <- lengthCodec.encode(bits.bytes)
        } yield lengthPrefixed
      }, { bits: BitVector =>
        for {
          result <- lengthCodec.decode(bits)
          a      <- valueCodec.decode(result.value.bits)
        } yield a.mapRemainder(_ => result.remainder)
      }
    )

  val codec: Codec[A] = prefixType match {
    case PrefixType.NoPrefix      => valueCodec
    case PrefixType.ItemLenPrefix => prefix(itemLength, valueCodec)
    case PrefixType.ListLenPrefix => prefix(listLengthCodec, valueCodec)
  }

  override def sizeBound: SizeBound = codec.sizeBound

  override def encode(a: A): Attempt[BitVector] =
    codec.encode(a)

  override def decode(buffer: BitVector): Attempt[DecodeResult[A]] =
    codec.decode(buffer)

  override def toString: String = prefixType match {
    case PrefixType.NoPrefix      => s"Codec(${valueCodec})"
    case PrefixType.ItemLenPrefix => s"RlpCodec(${valueCodec})"
    case PrefixType.ListLenPrefix => s"RlpListCodec(${valueCodec})"
  }
}

object RlpCodec {
  val itemOffset = 0x80

  val listOffset = 0xc0

  def lengthCodec(offset: Int): Codec[Either[Int, Long]] = new Codec[Either[Int, Long]] {
    val codec = codecs.uint8.consume[Either[Int, Long]] { h =>
      if (h < offset) codecs.provide(Left(h))
      else if ((h - offset) <= 55) codecs.provide(Right((h - offset).toLong))
      else codecs.ulong((h - offset - 55) * 8).xmap(Right.apply, _.right.get)
    } {
      case Left(l)             => l
      case Right(l) if l <= 55 => l.toInt + offset
      case Right(l)            => binaryLength(l) + offset + 55
    }

    override def encode(value: Either[Int, Long]): Attempt[BitVector]              = codec.encode(value)
    override def decode(bits: BitVector): Attempt[DecodeResult[Either[Int, Long]]] = codec.decode(bits)
    override def sizeBound: SizeBound                                              = SizeBound.atLeast(1L)
  }

  private def binaryLength(value: Long): Int = {
    @tailrec
    def bl0(value: Long, len: Int): Int =
      if (len >= 8 || value < (1L << (len * 8)))
        len
      else
        bl0(value, len + 1)

    bl0(value, 1)
  }

  val itemLength: Codec[ByteVector] = lengthCodec(itemOffset).consume[ByteVector] {
    case Left(v)  => codecs.provide(ByteVector(v)) // provide literal
    case Right(l) => codecs.bytes(l.toInt) // read next l bytes
  } { b =>
    if (b.length == 1 && b(0) >= 0x00 && b(0) <= 0x7f) Left(b(0).toInt) // [0x00, 0x7f]
    else Right(b.length) // how many bytes to read
  }

  val listLength: Codec[Long] = lengthCodec(listOffset)
    .narrow[Long]({
      case Left(l)  => Failure(Err(s"invalid rlp list length $l"))
      case Right(l) => Successful(l)
    }, Right.apply)

  val listLengthCodec: Codec[ByteVector] =
    listLength.consume[ByteVector](size => codecs.bytes(size.toInt))(_.length)

  def apply[A](implicit codec: RlpCodec[A]): RlpCodec[A] = codec

  def encode[A](a: A)(implicit c: RlpCodec[A]): Attempt[BitVector] = c.encode(a)

  def decode[A](bits: BitVector)(implicit c: RlpCodec[A]): Attempt[DecodeResult[A]] = c.decode(bits)

  type Typeclass[A] = RlpCodec[A]

  def combine[A](ctx: CaseClass[RlpCodec, A]): RlpCodec[A] = {
    val codec = new Codec[A] {
      override def encode(value: A): Attempt[BitVector] =
        Attempt.successful {
          ctx.parameters.foldLeft(BitVector.empty)((acc, cur) =>
            acc ++ cur.typeclass.encode(cur.dereference(value)).require)
        }

      override def decode(bits: BitVector): Attempt[DecodeResult[A]] = {
        val (fields, remainder) = ctx.parameters.foldLeft((List.empty[Any], bits)) {
          case ((acc, bits), cur) =>
            val v = cur.typeclass.decode(bits).require
            (v.value :: acc, v.remainder)
        }
        Attempt.successful(DecodeResult(ctx.rawConstruct(fields.reverse), remainder))
      }

      override def sizeBound: SizeBound =
        ctx.parameters.foldLeft(ctx.parameters.head.typeclass.sizeBound)((acc, cur) => acc + cur.typeclass.sizeBound)

      override def toString: String =
        s"${ctx.typeName.short}(${ctx.parameters.map(p => s"${p.label}").mkString(",")})"
    }

    if (ctx.isValueClass) {
      RlpCodec(PrefixType.NoPrefix, codec)
    } else {
      RlpCodec(PrefixType.ListLenPrefix, codec)
    }
  }

  def dispatch[A](ctx: SealedTrait[RlpCodec, A]): RlpCodec[A] = {
    val m1 = ctx.subtypes.zipWithIndex.map(_.swap).toMap
    val m2 = ctx.subtypes.zipWithIndex.toMap
    val codec = new Codec[A] {
      override def encode(value: A): Attempt[BitVector] =
        ctx.dispatch(value)(sub => {
          val bits = codecs.uint8.encode(m2(sub)).require
          sub.typeclass.encode(sub.cast(value)).map(v => bits ++ v)
        })

      override def decode(bits: BitVector): Attempt[DecodeResult[A]] =
        codecs.uint8.decode(bits).flatMap { result =>
          m1.get(result.value) match {
            case Some(sub) => sub.typeclass.decode(result.remainder)
            case None      => ???
          }
        }

      override def sizeBound: SizeBound = ???
    }

    RlpCodec(PrefixType.ItemLenPrefix, codec)
  }

  implicit def gen[A]: RlpCodec[A] = macro Magnolia.gen[A]
}
