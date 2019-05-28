package jbok.app.service.store

import jbok.core.api.HistoryTransaction
import jbok.core.models.{Block, Receipt}

trait TransactionStore[F[_]] {
  def findTransactionsByAddress(address: String, page: Int, size: Int): F[List[HistoryTransaction]]

  def findTransactionByHash(txHash: String): F[Option[HistoryTransaction]]

  def findTransactionsByNumber(blockNumber: Int): F[List[HistoryTransaction]]

  def delByBlockNumber(number: BigInt): F[Unit]

  def insertBlockTransactions(block: Block, receipts: List[Receipt]): F[Unit]
}
