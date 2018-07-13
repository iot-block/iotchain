package jbok.crypto.signature

import jbok.codec.codecs._
import scodec.Codec
import scodec.bits.ByteVector

case class CryptoSignature(bytes: ByteVector) extends AnyVal {
  def toHex: String = bytes.toHex
  def toArray: Array[Byte] = bytes.toArray
}

object CryptoSignature {
  def apply(bytes: Array[Byte]): CryptoSignature = CryptoSignature(ByteVector(bytes))

  implicit val codec: Codec[CryptoSignature] = codecBytes.as[CryptoSignature]
}
