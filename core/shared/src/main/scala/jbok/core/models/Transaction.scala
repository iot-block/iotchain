package jbok.core.models

import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs._

case class Transaction(
    nonce: BigInt,
    gasPrice: BigInt,
    gasLimit: BigInt,
    receivingAddress: Option[Address],
    value: BigInt,
    payload: ByteVector
) {
  val hash: ByteVector = ???
}

object Transaction {
  implicit val codec: Codec[Transaction] = {
    Codec[BigInt] ::
      Codec[BigInt] ::
      Codec[BigInt] ::
      optional(bool, Codec[Address]) ::
      Codec[BigInt] ::
      variableSizeBytes(uint8, bytes)
  }.as[Transaction]
}
