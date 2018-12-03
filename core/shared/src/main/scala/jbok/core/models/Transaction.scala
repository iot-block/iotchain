package jbok.core.models

import scodec.bits.ByteVector

final case class Transaction(
    nonce: BigInt,
    gasPrice: BigInt,
    gasLimit: BigInt,
    receivingAddress: Option[Address],
    value: BigInt,
    payload: ByteVector
)

object Transaction {
  def apply(
      nonce: BigInt,
      gasPrice: BigInt,
      gasLimit: BigInt,
      receivingAddress: Address,
      value: BigInt,
      payload: ByteVector
  ): Transaction =
    Transaction(
      nonce,
      gasPrice,
      gasLimit,
      Some(receivingAddress),
      value,
      payload
    )
}
