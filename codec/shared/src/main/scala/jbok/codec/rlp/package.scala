package jbok.codec

import java.net.URI
import java.util.UUID

import scodec._
import scodec.bits._

import scala.concurrent.duration.Duration

package object rlp {
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

    implicit val rlpArrayByte: RlpCodec[Array[Byte]] = rlp(arrayByte)

    implicit val rlpUuidCodec: RlpCodec[UUID] = rlp(codecs.uuid)

    implicit val rlpDurationCodec: RlpCodec[Duration] = rlp(duration)

    implicit val rlpUriCodec: RlpCodec[URI] = rlp(uri)

    implicit def rlpOptionalCodec[A](implicit codec: RlpCodec[A]): RlpCodec[Option[A]] =
      rlp[Option[A]](codecs.optional(bool, codec.valueCodec))

    implicit def rlpEitherCodec[L, R](implicit cl: RlpCodec[L], cr: RlpCodec[R]): RlpCodec[Either[L, R]] =
      rlp[Either[L, R]](codecs.either[L, R](bool, cl.valueCodec, cr.valueCodec))

    implicit def rlpListCodec[A](implicit codec: RlpCodec[A]): RlpCodec[List[A]] =
      rlplist[List[A]](codecs.list(codec))
  }
}
