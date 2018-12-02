package jbok.core.rlpx.handshake

import jbok.crypto.signature.CryptoSignature
import scodec.bits.ByteVector
import org.bouncycastle.util.BigIntegers.asUnsignedByteArray

object AuthInitiateEcdsaCodec {
  def encodeECDSA(sig: CryptoSignature): ByteVector =
    ByteVector(
      sig.r.toByteArray.reverse.padTo(32, 0.toByte).reverse ++
        sig.s.toByteArray.reverse.padTo(32, 0.toByte).reverse ++
        sig.r.toByteArray
    )

  def decodeECDSA(input: Array[Byte]): CryptoSignature = {
    val SIndex = 32
    val VIndex = 64

    val r = input.take(32)
    val s = input.slice(SIndex, SIndex + 32)
    val v = input.slice(SIndex + 64, input.length)
    CryptoSignature(BigInt(1, r), BigInt(1, s), BigInt(1, v))
  }
}
