package jbok.crypto.hash

import scodec.bits.ByteVector

trait CryptoHasherSyntax {
  implicit def cryptoHasherByteVectorSyntax(b: ByteVector) = new CryptoHasherByteVectorOps(b)
  implicit def cryptoHasherByteArraySyntax(b: Array[Byte]) = new CryptoHasherByteArrayOps(b)
}
