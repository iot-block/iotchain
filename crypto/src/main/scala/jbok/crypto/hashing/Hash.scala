package jbok.crypto.hashing

import scodec.Codec
import scodec.bits._
import scodec.codecs._

case class Hash(code: Byte, digest: ByteVector) {
  lazy val name = Hashing.codeNameMap(code)

  lazy val hasher = Hashing.codeTypeMap(code)

  override def toString: String = s"$name@${digest.toHex.take(7)}"
}

object Hash {
  implicit val codec: Codec[Hash] = {
    ("code" | byte) :: ("digest" | variableSizeBytes(uint8, bytes))
  }.as[Hash]
}
