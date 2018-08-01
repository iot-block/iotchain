package jbok.core.models

import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.codecs._
import jbok.crypto._
import scodec.bits.ByteVector

case class Transaction(
    nonce: BigInt,
    gasPrice: BigInt,
    gasLimit: BigInt,
    receivingAddress: Option[Address],
    value: BigInt,
    payload: ByteVector
) {
  lazy val hash: ByteVector = RlpCodec.encode(this).require.bytes.kec256

  def isContractInit: Boolean = receivingAddress.isEmpty
}

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
