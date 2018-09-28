package jbok.crypto.hash
import java.security.{MessageDigest, Security}

import org.bouncycastle.jcajce.provider.digest.Keccak
import org.bouncycastle.jce.provider.BouncyCastleProvider

object BouncyHash {
  if (Security.getProvider("BC") == null) {
    Security.addProvider(new BouncyCastleProvider())
  }

  def genInstance(algorithm: String): MessageDigest = MessageDigest.getInstance(algorithm, "BC")

  val sha256                     = genInstance("SHA-256")
  val ripemd160                  = genInstance("RipeMD160")
  def kec256(bytes: Array[Byte]) = new Keccak.Digest256().digest(bytes)
  def kec512(bytes: Array[Byte]) = new Keccak.Digest512().digest(bytes)
}
