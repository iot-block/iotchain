package jbok.crypto.hash

import scodec.bits.ByteVector

final class CryptoHasherByteVectorOps(val b: ByteVector) {
  def hash[H: CryptoHasher]: ByteVector = CryptoHasher[H].hash(b)

  def kec256: ByteVector = hash[Keccak256]

  def kec512: ByteVector = hash[Keccak512]

  def sha256: ByteVector = hash[SHA256]

  def ripemd160: ByteVector = hash[RipeMD160]
}

final class CryptoHasherByteArrayOps(val b: Array[Byte]) {
  def hash[H: CryptoHasher]: Array[Byte] = CryptoHasher[H].hash(b)

  def kec256: Array[Byte] = hash[Keccak256]

  def kec512: Array[Byte] = hash[Keccak512]

  def sha256: Array[Byte] = hash[SHA256]

  def ripemd160: Array[Byte] = hash[RipeMD160]
}
