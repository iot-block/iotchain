package jbok

import java.nio.charset.StandardCharsets
import java.util.Random

import jbok.crypto.hash._
import scodec.bits.ByteVector
import jbok.crypto.signature.SignatureInstances

trait StringSyntax {
  implicit final def stringSyntax(a: String): StringOps = new StringOps(a)
}

final class StringOps(val a : String) extends AnyVal {
  def utf8bytes: ByteVector = ByteVector(a.getBytes(StandardCharsets.UTF_8))
}

trait CryptoSyntax extends CryptoHasherSyntax with StringSyntax
trait CryptoInstances extends CryptoHasherInstances with SignatureInstances

package object crypto extends CryptoSyntax with CryptoInstances {
  def randomByteString(random: Random, length: Int): ByteVector =
    ByteVector(randomByteArray(random, length))

  def randomByteArray(random: Random, length: Int): Array[Byte] = {
    val bytes = Array.ofDim[Byte](length)
    random.nextBytes(bytes)
    bytes
  }
}
