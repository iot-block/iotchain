package jbok.core.models

import scodec.Codec
import scodec.bits.ByteVector
import tsec.hashing.bouncy._
import jbok.codec.codecs._

case class Transaction(
    nonce: BigInt,
    gasPrice: BigInt,
    gasLimit: BigInt,
    receivingAddress: Option[Address],
    value: BigInt,
    payload: ByteVector
) {
  lazy val hash: ByteVector =
    ByteVector(Keccak256.hashPure(Transaction.codec.encode(this).require.toByteArray))

  def isContractInit: Boolean = receivingAddress.isEmpty
}

object Transaction {
  implicit val codec: Codec[Transaction] = {
    codecBigInt ::
      codecBigInt ::
      codecBigInt ::
      codecOptional[Address] ::
      codecBigInt ::
      codecBytes
  }.as[Transaction]

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
