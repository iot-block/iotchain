package jbok.crypto.hash

import scodec.bits.ByteVector

sealed trait Keccak256
sealed trait Keccak512
sealed trait SHA256
sealed trait RipeMD160

trait CryptoHasher[A] {
  def hash(bytes: Array[Byte]): Array[Byte]

  def hash(bv: ByteVector): ByteVector = ByteVector(hash(bv.toArray))
}

object CryptoHasher {
  def apply[A](implicit ev: CryptoHasher[A]): CryptoHasher[A] = ev

  def hash[A: CryptoHasher](bytes: Array[Byte]): Array[Byte] = CryptoHasher[A].hash(bytes)

  def hash[A: CryptoHasher](bv: ByteVector): ByteVector = CryptoHasher[A].hash(bv)
}
