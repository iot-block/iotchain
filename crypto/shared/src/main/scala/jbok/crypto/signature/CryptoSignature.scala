package jbok.crypto.signature

import java.math.BigInteger
import java.security.SecureRandom

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

trait SignatureAlg[F[_]] {
  def generateKeyPair(secureRandom: SecureRandom): F[KeyPair]

  def generatePublicKey(secret: KeyPair.Secret): F[KeyPair.Public]

  def sign(hash: Array[Byte], keyPair: KeyPair, chainId: Option[Byte] = None): F[CryptoSignature]

  def verify(hash: Array[Byte], sig: CryptoSignature, public: KeyPair.Public): F[Boolean]
}

trait RecoverableSignatureAlg[F[_]] extends SignatureAlg[F] {
  def recoverPublic(hash: Array[Byte], sig: CryptoSignature, chainId: Option[Byte] = None): Option[KeyPair.Public]
}
