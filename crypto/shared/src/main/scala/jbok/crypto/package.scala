package jbok

import java.nio.charset.StandardCharsets
import java.security.SecureRandom

import cats.Id
import scodec.bits.ByteVector
import tsec.Bouncy
import tsec.hashing.jca._
import tsec.hashing.{CryptoHashAPI, _}
import tsec.hashing.bouncy.{genHasher, _}

abstract class AsBouncyCryptoHash[H](repr: String) extends BouncyDigestTag[H] with CryptoHashAPI[H] {

  /** Get our instance of jca crypto hash **/
  def hashPure(s: Array[Byte])(implicit C: CryptoHasher[Id, H], B: Bouncy): CryptoHash[H] = C.hash(s)

  def algorithm: String = repr

  implicit val tag: BouncyDigestTag[H] = this
}

sealed trait Keccak256
object Keccak256 extends AsBouncyCryptoHash[Keccak256]("KECCAK-256")

sealed trait Keccak512
object Keccak512 extends AsBouncyCryptoHash[Keccak512]("KECCAK-512")

trait HashingSyntax {
  implicit final def hashingSyntax(a: ByteVector): HashingOps = new HashingOps(a)
  implicit final def hashingSyntax2(a: Array[Byte]): HashingOps2 = new HashingOps2(a)
}

final class HashingOps(val a: ByteVector) extends AnyVal {
  def kec256: ByteVector = ByteVector(Keccak256.hashPure(a.toArray))

  def kec512: ByteVector = ByteVector(Keccak512.hashPure(a.toArray))

  def sha256: ByteVector = ByteVector(SHA256.hashPure(a.toArray))

  def ripemd160: ByteVector = ByteVector(RipeMD160.hashPure(a.toArray))
}

final class HashingOps2(val a: Array[Byte]) extends AnyVal {
  def kec256: Array[Byte] = Keccak256.hashPure(a)

  def kec512: Array[Byte] = Keccak512.hashPure(a)

  def sha256: Array[Byte] = SHA256.hashPure(a)

  def ripemd160: Array[Byte] = RipeMD160.hashPure(a)
}

trait StringSyntax {
  implicit final def stringSyntax(a: String): StringOps = new StringOps(a)
}

final class StringOps(val a : String) extends AnyVal {
  def utf8bytes: ByteVector = ByteVector(a.getBytes(StandardCharsets.UTF_8))
}

package object crypto extends HashingSyntax with StringSyntax {
  def secureRandomByteString(secureRandom: SecureRandom, length: Int): ByteVector =
    ByteVector(secureRandomByteArray(secureRandom, length))

  def secureRandomByteArray(secureRandom: SecureRandom, length: Int): Array[Byte] = {
    val bytes = Array.ofDim[Byte](length)
    secureRandom.nextBytes(bytes)
    bytes
  }
}
