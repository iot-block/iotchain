package jbok.network.rlpx.handshake

import jbok.crypto.signature.CryptoSignature
import scodec.bits.ByteVector
import org.bouncycastle.util.BigIntegers.asUnsignedByteArray

object AuthInitiateEcdsaCodec {
  def encodeECDSA(sig: CryptoSignature): ByteVector = {

    val recoveryId: Byte = (sig.v - 27).toByte

    ByteVector(
      asUnsignedByteArray(sig.r).reverse.padTo(32, 0.toByte).reverse ++
        asUnsignedByteArray(sig.s).reverse.padTo(32, 0.toByte).reverse ++
        Array(recoveryId)
    )
  }

  def decodeECDSA(input: Array[Byte]): CryptoSignature = {
    val SIndex = 32
    val VIndex = 64

    val r = input.take(32)
    val s = input.slice(SIndex, SIndex + 32)
    val v = input(VIndex) + 27
    CryptoSignature(BigInt(1, r), BigInt(1, s), v.toByte)
  }
}
