package jbok.crypto.hashing

import cats.Id
import scodec.Codec
import scodec.bits._
import scodec.codecs._
import tsec.common._
import tsec.hashing.CryptoHasher

case class MultiHash(code: Byte, size: Int, digest: ByteVector) {
  lazy val name = HashType.codeNameMap(code)

  override def toString: String = s"MultiHash($name, $size, $digest)"
}

object MultiHash {
  implicit val codec: Codec[MultiHash] = {
    ("code" | byte) ::
      (("digest size" | uint8) >>:~ { size =>
      ("digest" | bytes(size)).hlist
    })
  }.as[MultiHash]

  def hash[A](str: String, ht: HashType[A])(implicit C: CryptoHasher[Id, A]): MultiHash =
    hash(str.utf8Bytes, ht)

  def hash[A](bytes: Array[Byte], ht: HashType[A])(implicit C: CryptoHasher[Id, A]): MultiHash = {
    val digest = ht.cryptoHashAPI.hash[Id](bytes)(C)
    MultiHash(ht.code, ht.size, ByteVector(digest))
  }
}
