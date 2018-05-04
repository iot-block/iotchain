package jbok.core

import jbok.crypto.hashing.MultiHash
import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs._

case class Transaction(hash: MultiHash, length: Int, message: ByteVector)
object Transaction {
  implicit val codec: Codec[Transaction] = {
    ("hash" | Codec[MultiHash]) ::
      (("length" | int32) >>:~ { size =>
      ("message" | bytes(size)).hlist
    })
  }.as[Transaction]
}
