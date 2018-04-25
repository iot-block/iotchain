package jbok.crypto.hashing

import tsec.hashing.CryptoHashAPI
import tsec.hashing.bouncy._
import tsec.hashing.jca._

sealed abstract class HashType[A](val name: String, val code: Byte, val size: Int, val cryptoHashAPI: CryptoHashAPI[A])
object HashType {
  case object sha1 extends HashType("sha1", 0x11, 20, SHA1)
  case object sha256 extends HashType("sha2-256", 0x12, 32, SHA256)
  case object sha512 extends HashType("sha2-512", 0x13, 64, SHA512)
  case object blake2b extends HashType("blake2b-512", 0x40, 64, Blake2B512)

  val types = Seq(sha1, sha256, sha512, blake2b)

  val codeNameMap = types.map(x => x.code -> x.name).toMap
}
