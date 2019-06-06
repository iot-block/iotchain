package jbok.crypto.hash

import scodec.bits.ByteVector

sealed trait Keccak256
sealed trait Keccak512
sealed trait SHA256
sealed trait RipeMD160

trait CryptoHasher[A] {
  def hash(bytes: ByteVector): ByteVector
}

object CryptoHasher {
  def apply[A](implicit ev: CryptoHasher[A]): CryptoHasher[A] = ev
}
