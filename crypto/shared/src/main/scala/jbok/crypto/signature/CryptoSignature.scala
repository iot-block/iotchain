package jbok.crypto.signature

import jbok.codec.json.implicits._
import scodec.bits.ByteVector

final case class CryptoSignature(r: BigInt, s: BigInt, v: BigInt) {
  def bytes: Array[Byte] =
    CryptoSignature.unsignedPadding(r) ++ CryptoSignature.unsignedPadding(s) ++ CryptoSignature.unsigned(v)
}

object CryptoSignature {
  def unsigned(bigInteger: BigInt): Array[Byte] = {
    val asByteArray = bigInteger.toByteArray
    if (asByteArray.head == 0) asByteArray.tail else asByteArray
  }

  def unsignedPadding(bigInt: BigInt): Array[Byte] = {
    val bytes = unsigned(bigInt)
    ByteVector(bytes).take(32).padLeft(32).toArray
  }

  def apply(bytes: Array[Byte]): CryptoSignature = {
    require(bytes.length >= 65, s"signature length should be 65 instead of ${bytes.length}")
    CryptoSignature(
      BigInt(1, bytes.slice(0, 32)),
      BigInt(1, bytes.slice(32, 64)),
      BigInt(1, bytes.slice(64, bytes.length))
    )
  }

  def apply(r: ByteVector, s: ByteVector, v: ByteVector): CryptoSignature =
    CryptoSignature(BigInt(1, r.toArray), BigInt(1, s.toArray), BigInt(1, v.toArray))

  implicit val signatureJsonEncoder = deriveEncoder[CryptoSignature]

  implicit val signatureJsonDecoder = deriveDecoder[CryptoSignature]
}
