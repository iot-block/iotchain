package jbok.app.service.store.doobie

import cats.effect.Sync
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import doobie.util.update.Update
import jbok.app.service.models.Transaction
import jbok.app.service.store.TransactionStore
import jbok.core.models.{Receipt, Block => CoreBlock}

final class DoobieTransactionStore[F[_]](xa: Transactor[F])(implicit F: Sync[F]) extends TransactionStore[F] {

  def findAllTxs(page: Int, size: Int): F[List[Transaction]] =
    (sql"""
       SELECT txHash, nonce, fromAddress, toAddress, value, payload, v, r, s, gasUsed, gasPrice, blockNumber, blockHash, location
       FROM transactions
       ORDER BY blockNumber, location DESC
    """ ++ Doobie.limit(page, size))
      .query[Transaction]
      .to[List]
      .transact(xa)

  def findTransactionsByAddress(address: String, page: Int, size: Int): F[List[Transaction]] =
    (sql"""
       SELECT txHash, nonce, fromAddress, toAddress, value, payload, v, r, s, gasUsed, gasPrice, blockNumber, blockHash, location
       FROM transactions
       WHERE (fromAddress = ${address.toString} OR toAddress = ${address.toString})
       ORDER BY blockNumber, location DESC
      """ ++ Doobie.limit(page, size))
      .query[Transaction]
      .to[List]
      .transact(xa)

  def findTransactionByHash(txHash: String): F[Option[Transaction]] =
    sql"""
       SELECT txHash, nonce, fromAddress, toAddress, value, payload, v, r, s, gasUsed, gasPrice, blockNumber, blockHash, location
       FROM transactions
       WHERE txHash = ${txHash}
       """
      .query[Transaction]
      .option
      .transact(xa)

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
      s"insert ignore into transactions (txHash, nonce, fromAddress, toAddress, value, payload, v, r, s, gasUsed, gasPrice, blockNumber, blockHash, location) values ($holes)"
    Update[(String, Int, String, String, String, String, String, String, String, String, String, Long, String, Int)](sql)
      .updateMany(xs)
      .transact(xa)
      .void
  }
}
