package jbok.core

import jbok.crypto.hashing.Hash
import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs._

case class Transaction(hash: Hash, length: Int, message: ByteVector)
object Transaction {
  implicit val codec: Codec[Transaction] = {
    ("hash" | Codec[Hash]) ::
      (("length" | int32) >>:~ { size =>
      ("message" | bytes(size)).hlist
    })
  }.as[Transaction]
}