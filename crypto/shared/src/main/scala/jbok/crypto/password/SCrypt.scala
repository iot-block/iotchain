package jbok.crypto.password

import java.nio.charset.StandardCharsets

import org.bouncycastle.crypto.generators.{SCrypt => BouncySCrypt}
import scodec.bits.ByteVector

object SCrypt {
  def derive(passphrase: String, salt: ByteVector, n: Int, r: Int, p: Int, dkLen: Int): ByteVector = {
    val key = BouncySCrypt.generate(passphrase.getBytes(StandardCharsets.UTF_8), salt.toArray, n, r, p, dkLen)
    ByteVector(key)
  }
}
