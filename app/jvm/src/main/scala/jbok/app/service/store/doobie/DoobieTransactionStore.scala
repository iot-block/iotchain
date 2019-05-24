package jbok.app.service.store.doobie

import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import doobie.util.update.Update
import jbok.app.service.store.TransactionStore
import jbok.core.api.HistoryTransaction
import jbok.core.models.{Receipt, Block => CoreBlock}

final class DoobieTransactionStore[F[_]](xa: Transactor[F])(implicit F: Sync[F]) extends TransactionStore[F] with DoobieSupport {
  def findTransactionsByAddress(address: String, page: Int, size: Int): F[List[HistoryTransaction]] =
    sql"""
       SELECT txHash, nonce, fromAddress, toAddress, value, payload, v, r, s, gasUsed, gasPrice, blockNumber, blockHash, location
       FROM transactions
       WHERE (fromAddress = ${address.toString} OR toAddress = ${address.toString})
       ORDER BY blockNumber, location DESC
       limit ${size} offset ${(page - 1) * size}
      """
      .query[HistoryTransaction]
      .to[List]
      .transact(xa)

  def findTransactionByHash(txHash: String): F[Option[HistoryTransaction]] =
    sql"""
       SELECT txHash, nonce, fromAddress, toAddress, value, payload, v, r, s, gasUsed, gasPrice, blockNumber, blockHash, location
       FROM transactions
       WHERE txHash = ${txHash}
       """
      .query[HistoryTransaction]
      .option
      .transact(xa)

  override def delByBlockNumber(number: BigInt): F[Unit] =
    sql"""DELETE from transactions WHERE blockNumber = $number""".update.run.void.transact(xa)

  def insertBlockTransactions(block: CoreBlock, receipts: List[Receipt]): F[Unit] = {
    require(block.body.transactionList.length == receipts.length)
    val xs = block.body.transactionList.zip(receipts).zipWithIndex.map {
      case ((stx, receipt), idx) =>
        (
          stx.hash.toHex,
          stx.nonce.toInt,
          stx.senderAddress.map(_.toString).getOrElse(""),
          stx.receivingAddress.toString,
          stx.value.toString,
          stx.payload.toHex,
          stx.v.toString,
          stx.r.toString,
          stx.s.toString,
          receipt.gasUsed.toString,
          stx.gasPrice.toString,
          block.header.number.toLong,
          block.header.hash.toHex,
          idx
        )
    }
    val holes = List.fill(14)("?").mkString(",")
    val sql =
      s"insert into transactions (txHash, nonce, fromAddress, toAddress, value, payload, v, r, s, gasUsed, gasPrice, blockNumber, blockHash, location) values ($holes)"
    Update[(String, Int, String, String, String, String, String, String, String, String, String, Long, String, Int)](sql)
      .updateMany(xs)
      .transact(xa)
      .void
  }
}
