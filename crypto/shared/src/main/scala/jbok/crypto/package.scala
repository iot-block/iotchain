package jbok

import java.nio.charset.StandardCharsets

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

trait HashingSyntax {
  implicit final def hashingSyntax(a: ByteVector): HashingOps = new HashingOps(a)
}

final class HashingOps(val a: ByteVector) extends AnyVal {
  def kec256: ByteVector = ByteVector(Keccak256.hashPure(a.toArray))

  def sha256: ByteVector = ByteVector(SHA256.hashPure(a.toArray))

  def ripemd160: ByteVector = ByteVector(RipeMD160.hashPure(a.toArray))
}

trait StringSyntax {
  implicit final def stringSyntax(a: String): StringOps = new StringOps(a)
}

final class StringOps(val a : String) extends AnyVal {
  def utf8bytes: ByteVector = ByteVector(a.getBytes(StandardCharsets.UTF_8))
}

package object crypto extends HashingSyntax with StringSyntax
