package jbok.crypto.signature

import java.math.BigInteger

import jbok.codec.codecs._
import scodec.Codec
import scodec.bits.ByteVector

case class CryptoSignature(r: BigInt, s: BigInt, v: Option[Byte]) {
  def bytes: ByteVector = ByteVector(r.toByteArray) ++ ByteVector(s.toByteArray)
}

object CryptoSignature {
  implicit val codec: Codec[CryptoSignature] = (codecBigInt :: codecBigInt :: codecOptional[Byte]).as[CryptoSignature]

  def apply(r: BigInteger, s: BigInteger, v: Option[Byte]): CryptoSignature =
    CryptoSignature(BigInt(r), BigInt(s), v)

  def apply(r: ByteVector, s: ByteVector, v: Option[Byte]): CryptoSignature =
    CryptoSignature(BigInt(1, r.toArray), BigInt(1, s.toArray), v)
}
