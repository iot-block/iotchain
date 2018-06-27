package jbok.core.models

import jbok.crypto.signature.Signature
import scodec.bits.ByteVector

case class SignedTransaction(
    tx: Transaction,
    signature: Signature,
    senderAddress: Address
) {
  val hash: ByteVector = ???
}
