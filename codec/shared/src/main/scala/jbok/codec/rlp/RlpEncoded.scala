package jbok.codec.rlp

import scodec.bits.{BitVector, ByteVector}

// normally we construct `RlpEncoded` via rlp encoding
// to avoid dumb problems
final class RlpEncoded private (val bits: BitVector) extends AnyVal {
  def bytes: ByteVector = bits.bytes

  def byteArray: Array[Byte] = bits.toByteArray

  def decoded[A](implicit codec: RlpCodec[A]): Either[Throwable, A] =
    codec.decode(bits).toEither match {
      case Left(err)     => Left(new Exception(err.messageWithContext))
      case Right(result) => Right(result.value)
    }

  def isItem: Boolean =
    bits.bytes.headOption match {
      case Some(byte) => (0xff & byte) < RlpCodecHelper.listOffset
      case None       => false
    }

  def isList: Boolean =
    !isItem

  def isEmptyItem: Boolean =
    this == RlpEncoded.emptyItem

  def isEmptyList: Boolean =
    this == RlpEncoded.emptyList

  override def toString: String = s"RlpEncoded(${bits.bytes})"
}

object RlpEncoded {
  def coerce(bits: BitVector): RlpEncoded = new RlpEncoded(bits)

  val emptyItem: RlpEncoded = RlpEncoded.coerce(BitVector(RlpCodecHelper.itemOffset.toByte))

  val emptyList: RlpEncoded = RlpEncoded.coerce(BitVector(RlpCodecHelper.listOffset.toByte))
}
