package jbok.codec

import java.nio.charset.{Charset, StandardCharsets}

import scodec.Attempt.{Failure, Successful}
import scodec._
import scodec.bits._
import scodec.codecs._

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

  private def rlpLength(offset: Int): Codec[Either[Int, Long]] =
    uint8.consume[Either[Int, Long]] { h =>
      if (h < offset) provide(Left(h))
      else if ((h - offset) < 56) provide(Right(h.toLong - offset))
      else ulong((h - offset - 55) * 8).xmap(Right.apply, _.right.get)
    } {
      case Left(l) => l.toInt
      case Right(l) if l < 56 => l.toInt + offset
      case Right(l) => scalarLength(l) + 55 + offset
    }

  private def rlpLength31(offset: Int): Codec[Either[Int, Int]] =
    rlpLength(offset).narrow[Either[Int, Int]](
      {
        case Left(v) => Successful(Left(v))
        case Right(len) if len.isValidInt => Successful(Right(len.toInt))
        case Right(len) => Failure(Err(s"length out of range (length: $len)"))
      },
      _.right.map(_.toLong)
    )

  val bytesLen: Codec[Either[Int, Int]] = rlpLength31(0x80)

  val listLen: Codec[Int] = rlpLength31(0xc0).narrow[Int]({
    case Left(bad) => Failure(Err(s"invalid RLP list header $bad"))
    case Right(len) => Successful(len)
  }, Right.apply)

  private def scalarLength(value: Long) =
    math.max(java.lang.Long.SIZE - java.lang.Long.numberOfLeadingZeros(value), 8) / 8

  implicit val rlpBytes: RlpCodec[ByteVector] = RlpCodec(
    bytesLen.consume {
      case Left(v) => provide(ByteVector(v)) // Single byte value < 128
      case Right(l) => bytes(l)
    } { b =>
      if (b.length == 1 && b(0) > 0 && b(0) < 128) Left(b(0))
      else Right(b.length.toInt)
    }
  )

  implicit def rlpString(implicit charset: Charset = StandardCharsets.UTF_8): RlpCodec[String] =
    RlpCodec(rlpBytes.narrow[String](s => string.decode(s.bits).map(_.value), s => ByteVector(s.getBytes(charset))))

  implicit def rlpList[A](implicit codec: RlpCodec[A]): RlpCodec[List[A]] =
    RlpCodec(variableSizeBytes(listLen, list(codec)))

  implicit def rlpCodec[A](codec: Codec[A]): RlpCodec[A] = RlpCodec(codec)

  def encode[A](a: A)(implicit rlpCodec: RlpCodec[A]): Attempt[BitVector] = rlpCodec.encode(a)

  def decode[A](bits: BitVector)(implicit rlpCodec: RlpCodec[A]): Attempt[DecodeResult[A]] = rlpCodec.decode(bits)
}
