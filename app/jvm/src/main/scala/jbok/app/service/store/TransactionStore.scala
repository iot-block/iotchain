package jbok.app.service.store

import jbok.app.service.models.Transaction
import jbok.core.models.{Receipt, Block => CoreBlock}

trait TransactionStore[F[_]] {
  def findAllTxs(page: Int, size: Int): F[List[Transaction]]

  def findTransactionsByAddress(address: String, page: Int, size: Int): F[List[Transaction]]

  def findTransactionByHash(txHash: String): F[Option[Transaction]]

  def insertBlockTransactions(block: CoreBlock, receipts: List[Receipt]): F[Unit]
}
