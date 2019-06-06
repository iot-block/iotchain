package jbok.crypto.hash

import scodec.bits.ByteVector

trait CryptoHasherSyntax {
  implicit final def cryptoHasherSyntax(bytes: ByteVector): CryptoHasherOps = new CryptoHasherOps(bytes)
}
