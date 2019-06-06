package jbok.crypto.hash
import java.security.{MessageDigest, Security}

import org.bouncycastle.jcajce.provider.digest.Keccak
import org.bouncycastle.jce.provider.BouncyCastleProvider
import scodec.bits.ByteVector

object BouncyHash {
  if (Security.getProvider("BC") == null) {
    Security.addProvider(new BouncyCastleProvider())
  }

  def genInstance(algorithm: String): MessageDigest = MessageDigest.getInstance(algorithm, "BC")

  val sha256                    = genInstance("SHA-256")
  val ripemd160                 = genInstance("RipeMD160")
  def kec256(bytes: ByteVector) = new Keccak.Digest256().digest(bytes.toArray)
  def kec512(bytes: ByteVector) = new Keccak.Digest512().digest(bytes.toArray)
}
