package jbok.crypto.signature

import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs._

case class Signature private (sign: ByteVector) extends AnyVal {
  def bytes: Array[Byte] = sign.toArray
}

object Signature {
  def apply(sign: ByteVector): Signature = new Signature(sign)

  def apply(sign: Array[Byte]): Signature = new Signature(ByteVector(sign))

  implicit val codec: Codec[Signature] = variableSizeBytes(uint8, bytes).as[Signature]
}
