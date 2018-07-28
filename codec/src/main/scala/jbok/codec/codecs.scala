package jbok.codec

import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs._

object codecs extends codecs
trait codecs {
  implicit val codecString: Codec[String] = rlp.rstring

  implicit val codecByte: Codec[Byte] = rlp.rbyte

  implicit val codecBytes: Codec[ByteVector] = rlp.ritem

  implicit val codecArrayByte: Codec[Array[Byte]] = rlp.ritem.xmap[Array[Byte]](_.toArray, ByteVector.apply)

  implicit val codecBigInt: Codec[BigInt] =
    codecBytes.xmap[BigInt](bytes => if (bytes.isEmpty) 0 else BigInt(bytes.toArray), bi => ByteVector(bi.toUnsignedByteArray))

  implicit val codecLong: Codec[Long] = codecBigInt.xmap[Long](_.toLong, BigInt.apply)

  implicit def codecList[A: Codec]: Codec[List[A]] = rlp.rlist[A](rlp.RlpCodec(Codec[A]))

  implicit def codecSet[A: Codec]: Codec[Set[A]] = list(implicitly[Codec[A]]).xmap[Set[A]](_.toSet, _.toList)

  implicit val codecBoolean: Codec[Boolean] = bool

  implicit def codecOptional[A: Codec]: Codec[Option[A]] = optional(bool, Codec[A])

  implicit def codecEither[L: Codec, R: Codec] = either[L, R](bool, Codec[L], Codec[R])
}
