package jbok.crypto.hash

import scodec.bits.ByteVector

trait CryptoHasherSyntax {
  implicit def cryptoHasherByteVectorSyntax(b: ByteVector): CryptoHasherByteVectorOps = new CryptoHasherByteVectorOps(b)
  implicit def cryptoHasherByteArraySyntax(b: Array[Byte]): CryptoHasherByteArrayOps  = new CryptoHasherByteArrayOps(b)
}
