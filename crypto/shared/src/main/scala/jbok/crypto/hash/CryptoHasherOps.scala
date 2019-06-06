package jbok.crypto.hash

import scodec.bits.ByteVector

final class CryptoHasherOps(val bv: ByteVector) extends AnyVal {
  def kec256: ByteVector =
    CryptoHasher[Keccak256].hash(bv)

  def kec512: ByteVector =
    CryptoHasher[Keccak512].hash(bv)

  def sha256: ByteVector =
    CryptoHasher[SHA256].hash(bv)

  def ripemd160: ByteVector =
    CryptoHasher[RipeMD160].hash(bv)
}
