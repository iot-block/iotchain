package jbok.codec

import java.nio.charset.{Charset, StandardCharsets}

import scodec.Attempt.{Failure, Successful}
import scodec._
import scodec.bits._
import scodec.codecs._

import scala.annotation.tailrec
import scala.math.Numeric._

package object rlp {
  sealed trait RlpCodec[A] extends Codec[A]

  object RlpCodec {
    // Wraps a normal codec
    def apply[A](codec: Codec[A]): RlpCodec[A] = codec match {
      case c: RlpCodec[A] => c
      case c =>
        new RlpCodec[A] {
          val codec = c
          override def sizeBound: SizeBound = codec.sizeBound
          override def decode(bits: BitVector): Attempt[DecodeResult[A]] = codec.decode(bits)
          override def encode(value: A): Attempt[BitVector] = codec.encode(value)
        }
    }
  }

  val itemOffset = 0x80

  val listOffset = 0xc0

  private[jbok] def lengthCodec(offset: Int): Codec[Either[Int, Long]] = {
    uint8.consume[Either[Int, Long]] { h =>
      if (h < offset) provide(Left(h))
      else if ((h - offset) <= 55) provide(Right(h - offset))
      else ulong((h - offset - 55) * 8).xmap(Right.apply, _.right.get)
    } {
      case Left(l) => l
      case Right(l) if l <= 55 => l.toInt + offset
      case Right(l) => binaryLength(l) + offset + 55
    }
  }

  private[jbok] def binaryLength(value: Long): Int = {
    @tailrec
    def bl0(value: Long, len: Int): Int = {
      if (len >= 8 || value < (1L << (len * 8)))
        len
      else
        bl0(value, len + 1)
    }

    bl0(value, 1)
  }

  private[jbok] val itemLen: Codec[Either[Int, Long]] = lengthCodec(itemOffset)

  private[jbok] val listLen: Codec[Long] = lengthCodec(listOffset).narrow[Long]({
    case Left(l) => Failure(Err(s"invalid rlp list length $l"))
    case Right(l) => Successful(l)
  }, Right.apply)

  implicit val ritem: RlpCodec[ByteVector] = RlpCodec(
    itemLen.consume[ByteVector] {
      case Left(v) => provide(ByteVector(v)) // Single byte value < 128
      case Right(l) => bytes(l.toInt)
    } { b =>
      if (b.length == 1 && b(0) >= 0 && b(0) < 128) Left(b(0))
      else Right(b.length.toInt)
    }
  )

  implicit val rid: RlpCodec[ByteVector] = RlpCodec(scodec.codecs.bytes)

  implicit val rlist2: RlpCodec[List[ByteVector]] = rlist(rid)

  implicit val rlist: RlpCodec[List[ByteVector]] = rlist(ritem)

  implicit def rlist[A](codec: RlpCodec[A]): RlpCodec[List[A]] = RlpCodec(
    variableSizeBytes(listLen.xmap(_.toInt, _.toLong), list(codec))
  )

  implicit def rstruct[A](codec: Codec[A]): RlpCodec[A] =
    RlpCodec(variableSizeBytes(listLen.xmap(_.toInt, _.toLong), codec))

  implicit def ritem[A](codec: Codec[A]): RlpCodec[A] = RlpCodec(
    Codec(
      { a: A =>
        codec
          .encode(a)
          .flatMap(bits => ritem.encode(bits.bytes))
      }, { bits =>
        for {
          result1 <- ritem.decode(bits)
          result2 <- codec.decode(result1.value.bits)
        } yield result2.mapRemainder(_ => result1.remainder)
      }
    )
  )

  implicit val charset: Charset = StandardCharsets.UTF_8

  implicit val rstring: RlpCodec[String] = ritem(string)

  val ruint8: RlpCodec[Int] = RlpCodec(uint8.consume[Int] { h =>
    if (h < 0) fail(Err(s"negative scalar value $h"))
    else if (h < 128) provide(h)
    else if (h == 129) uint8
    else fail(Err(s"invalid RLP single byte header $h"))
  } { i =>
    if (i < 128) i
    else 129
  })

  val rbyte: RlpCodec[Byte] = RlpCodec(ritem.xmap[Byte](_.head, b => ByteVector(b)))

  val rempty: RlpCodec[Unit] = RlpCodec(constant(itemOffset))

  val ritemOpt: RlpCodec[Option[ByteVector]] = ritem(optional(bool(8), bytes))

  def rulong(bits: Int): RlpCodec[Long] = ritem(ulong(bits))

  def encode[A](a: A)(implicit codec: RlpCodec[A]): Attempt[BitVector] = codec.encode(a)

  def decode[A](bits: BitVector)(implicit codec: RlpCodec[A]): Attempt[DecodeResult[A]] = codec.decode(bits)
}
