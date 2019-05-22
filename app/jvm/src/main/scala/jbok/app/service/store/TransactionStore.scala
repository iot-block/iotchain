package jbok.app.service.store

import jbok.core.api.HistoryTransaction
import jbok.core.models.{Block, Receipt}

trait TransactionStore[F[_]] {
  def findTransactionsByAddress(address: String, page: Int, size: Int): F[List[HistoryTransaction]]

  def findTransactionByHash(txHash: String): F[Option[HistoryTransaction]]

  def insertBlockTransactions(block: Block, receipts: List[Receipt]): F[Unit]
}
