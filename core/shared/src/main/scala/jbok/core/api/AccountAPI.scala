package jbok.core.api

import io.circe.generic.extras.ConfiguredJsonCodec
import jbok.core.models.{Account, Address, SignedTransaction}
import scodec.bits.ByteVector
import jbok.codec.json.implicits._
import jbok.common.math.N
import jbok.network.rpc.PathName

@ConfiguredJsonCodec
final case class HistoryTransaction(
    txHash: ByteVector,
    nonce: N,
    fromAddress: Address,
    toAddress: Address,
    value: N,
    payload: String,
    v: N,
    r: N,
    s: N,
    gasUsed: N,
    gasPrice: N,
    blockNumber: N,
    blockHash: ByteVector,
    location: Int
)

@PathName("account")
trait AccountAPI[F[_]] {
  def getAccount(address: Address, tag: BlockTag = BlockTag.latest): F[Account]

  def getCode(address: Address, tag: BlockTag = BlockTag.latest): F[ByteVector]

  def getBalance(address: Address, tag: BlockTag = BlockTag.latest): F[N]

  def getStorageAt(address: Address, position: N, tag: BlockTag = BlockTag.latest): F[ByteVector]

  def getTransactions(address: Address, page: Int, size: Int): F[List[HistoryTransaction]]

  def getTransactionsByNumber(number: Int): F[List[HistoryTransaction]]

//  def getTokenTransactions(address: Address, contract: Option[Address]): F[List[SignedTransaction]]

  def getPendingTxs(address: Address): F[List[SignedTransaction]]

  def getEstimatedNonce(address: Address): F[N]
}
