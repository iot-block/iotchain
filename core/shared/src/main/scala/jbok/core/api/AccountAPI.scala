package jbok.core.api

import io.circe.generic.JsonCodec
import jbok.core.models.{Account, Address, SignedTransaction}
import scodec.bits.ByteVector
import jbok.codec.json.implicits._

@JsonCodec
final case class HistoryTransaction(
    txHash: ByteVector,
    nonce: BigInt,
    fromAddress: Address,
    toAddress: Address,
    value: BigInt,
    payload: String,
    v: BigInt,
    r: BigInt,
    s: BigInt,
    gasUsed: BigInt,
    gasPrice: BigInt,
    blockNumber: BigInt,
    blockHash: ByteVector,
    location: Int
)

trait AccountAPI[F[_]] {
  def getCode(address: Address, tag: BlockTag = BlockTag.latest): F[ByteVector]

  def getAccount(address: Address, tag: BlockTag = BlockTag.latest): F[Account]

  def getBalance(address: Address, tag: BlockTag = BlockTag.latest): F[BigInt]

  def getStorageAt(address: Address, position: BigInt, tag: BlockTag = BlockTag.latest): F[ByteVector]

  def getTransactions(address: Address): F[List[HistoryTransaction]]

//  def getTokenTransactions(address: Address, contract: Option[Address]): F[List[SignedTransaction]]

  def getPendingTxs(address: Address): F[List[SignedTransaction]]

  def getEstimatedNonce(address: Address): F[BigInt]
}
