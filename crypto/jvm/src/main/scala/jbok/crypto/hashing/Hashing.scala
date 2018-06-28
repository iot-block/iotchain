package jbok.crypto.hashing

import cats.Id
import scodec.bits.ByteVector
import simulacrum.typeclass
import tsec.hashing._
import tsec.hashing.bouncy._
import tsec.hashing.jca._

@typeclass trait Hashing[A] {
  def name: String

  def code: Byte

  def digest(bytes: ByteVector): ByteVector

  def hash(bytes: ByteVector): Hash
}

object Hashing {
  def digest[A](bytes: ByteVector)(implicit H: Hashing[A]): ByteVector =
    H.digest(bytes)

  def hash[A](bytes: ByteVector)(implicit H: Hashing[A]): Hash =
    H.hash(bytes)

  def instance[A](_name: String, _code: Byte, _size: Int)(implicit hasher: CryptoHasher[Id, A]) = new Hashing[A] {
    override val name: String = _name

    override val code: Byte = _code

    override def digest(bytes: ByteVector): ByteVector = ByteVector(hasher.hash(bytes.toArray))

    override def hash(bytes: ByteVector): Hash =
      Hash(_code, digest(bytes))
  }

  implicit val sha1 = instance[SHA1]("sha1", 0x11, 20)
  implicit val sha256 = instance[SHA256]("sha2-256", 0x12, 32)
  implicit val sha512 = instance[SHA512]("sha2-512", 0x13, 64)
  implicit val blake2b = instance[Blake2B512]("blake2b-512", 0x40, 64)

  val codeTypeMap: Map[Byte, Hashing[_]] = Map(
    sha1.code -> sha1,
    sha256.code -> sha256,
    sha512.code -> sha512,
    blake2b.code -> blake2b,
  )

  val codeNameMap: Map[Byte, String] = codeTypeMap.mapValues(_.name)
}

trait HashingSyntax {
  implicit final def hashingSyntax(a: ByteVector): HashingOps = new HashingOps(a)
}

final class HashingOps(val a: ByteVector) extends AnyVal {
  def digested[A](implicit H: Hashing[A]) = H.digest(a)
  def hashed[A](implicit H: Hashing[A]) = H.hash(a)
}
