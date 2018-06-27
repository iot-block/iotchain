package jbok.core.models

import jbok.crypto.signature.Signature
import scodec.Codec
import scodec.bits.ByteVector

case class SignedTransaction(
    tx: Transaction,
    signature: Signature,
    senderAddress: Address
) {
  val hash: ByteVector = ???
}

object SignedTransaction {
  implicit val codec: Codec[SignedTransaction] = {
    Codec[Transaction] ::
    Codec[Signature] ::
    Codec[Address]
  }.as[SignedTransaction]
}