package jbok

import java.nio.charset.StandardCharsets

import scodec.bits.ByteVector
import tsec.hashing.bouncy._
import tsec.hashing.jca._

trait HashingSyntax {
  implicit final def hashingSyntax(a: ByteVector): HashingOps = new HashingOps(a)
}

final class HashingOps(val a: ByteVector) extends AnyVal {
  def kec256: ByteVector = ByteVector(Keccak256.hashPure(a.toArray))

  def sha256: ByteVector = ByteVector(SHA256.hashPure(a.toArray))
}

trait StringSyntax {
  implicit final def stringSyntax(a: String): StringOps = new StringOps(a)
}

final class StringOps(val a : String) extends AnyVal {
  def utf8bytes: ByteVector = ByteVector(a.getBytes(StandardCharsets.UTF_8))
}

package object crypto extends HashingSyntax with StringSyntax
