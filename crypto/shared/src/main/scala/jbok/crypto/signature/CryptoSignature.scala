package jbok.crypto.signature

import java.math.BigInteger

import scodec.bits.ByteVector

final case class CryptoSignature(r: BigInteger, s: BigInteger, v: Byte) {
  def bytes: Array[Byte] =
    CryptoSignature.unsigned(r) ++ CryptoSignature.unsigned(s) ++ Array(v)
}

object CryptoSignature {
  def unsigned(bigInteger: BigInteger): Array[Byte] = {
    val asByteArray = bigInteger.toByteArray
    val bytes       = if (asByteArray.head == 0) asByteArray.tail else asByteArray
    ByteVector(bytes).take(32).padLeft(32).toArray
  }

  def apply(bytes: Array[Byte]): CryptoSignature = {
    require(bytes.length == 65, s"signature length should be 65 instead of ${bytes.length}")
    CryptoSignature(
      new BigInteger(1, bytes.slice(0, 32).toArray),
      new BigInteger(1, bytes.slice(32, 64).toArray),
      bytes.last
    )
  }

  def apply(r: BigInt, s: BigInt, v: Byte): CryptoSignature =
    CryptoSignature(r.underlying(), s.underlying(), v)

  def apply(r: ByteVector, s: ByteVector, v: Byte): CryptoSignature =
    CryptoSignature(BigInt(1, r.toArray), BigInt(1, s.toArray), v)
}
