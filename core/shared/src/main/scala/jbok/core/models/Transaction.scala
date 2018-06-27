package jbok.core.models

import scodec.bits.ByteVector

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
