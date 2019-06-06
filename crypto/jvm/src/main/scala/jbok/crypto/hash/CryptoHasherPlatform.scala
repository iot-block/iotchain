package jbok.crypto.hash

import scodec.bits.ByteVector

trait CryptoHasherPlatform {
  implicit val kec256Platform: CryptoHasher[Keccak256] = new CryptoHasher[Keccak256] {
    override def hash(bytes: ByteVector): ByteVector = ByteVector(BouncyHash.kec256(bytes))
  }

  implicit val kec512Platform: CryptoHasher[Keccak512] = new CryptoHasher[Keccak512] {
    override def hash(bytes: ByteVector): ByteVector = ByteVector(BouncyHash.kec512(bytes))
  }

  implicit val sha256Platform: CryptoHasher[SHA256] = new CryptoHasher[SHA256] {
    override def hash(bytes: ByteVector): ByteVector = ByteVector(BouncyHash.sha256.digest(bytes.toArray))
  }

  implicit val ripemd160Platform: CryptoHasher[RipeMD160] = new CryptoHasher[RipeMD160] {
    override def hash(bytes: ByteVector): ByteVector = ByteVector(BouncyHash.ripemd160.digest(bytes.toArray))
  }
}
