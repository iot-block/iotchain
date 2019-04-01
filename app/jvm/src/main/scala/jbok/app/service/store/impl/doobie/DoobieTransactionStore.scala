package jbok.app.service.store.impl.doobie

import cats.effect.IO
import jbok.app.service.store.TransactionStore
import cats.effect.IO
import doobie.implicits._
import doobie.util.transactor.Transactor
import doobie.util.update.Update
import jbok.app.service.models.Transaction
import jbok.core.models.{Receipt, Block => CoreBlock}
import cats.implicits._

@SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
class DoobieTransactionStore(xa: Transactor[IO]) extends TransactionStore[IO] {

  def findAllTxs(page: Int, size: Int): IO[List[Transaction]] =
    sql"""
       SELECT txHash, nonce, fromAddress, toAddress, value, payload, v, r, s, gasUsed, gasPrice, blockNumber, blockHash, location
       FROM transactions
       ORDER BY blockNumber, location DESC
       LIMIT ${size}
       OFFSET ${(page - 1) * size}
       """.query[Transaction].to[List].transact(xa)

  def findTransactionsByAddress(address: String, page: Int, size: Int): IO[List[Transaction]] =
    sql"""
       SELECT txHash, nonce, fromAddress, toAddress, value, payload, v, r, s, gasUsed, gasPrice, blockNumber, blockHash, location
       FROM transactions
       WHERE (fromAddress = ${address.toString} OR toAddress = ${address.toString})
       ORDER BY blockNumber, location DESC
       LIMIT ${size}
       OFFSET ${(page - 1) * size}
       """.query[Transaction].to[List].transact(xa)

  def findTransactionByHash(txHash: String): IO[Option[Transaction]] =
    sql"""
       SELECT txHash, nonce, fromAddress, toAddress, value, payload, v, r, s, gasUsed, gasPrice, blockNumber, blockHash, location
       FROM transactions
       WHERE txHash = ${txHash}
       """
      .query[Transaction]
      .option
      .transact(xa)

  def insertBlockTransactions(block: CoreBlock, receipts: List[Receipt]) = {
    require(block.body.transactionList.length == receipts.length)
    val xs = block.body.transactionList.zip(receipts).zipWithIndex.map {
      case ((stx, receipt), idx) =>
        (
          stx.hash.toHex,
          stx.nonce.toInt,
          stx.senderAddress.get.toString,
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
      s"INSERT OR IGNORE INTO transactions (txHash, nonce, fromAddress, toAddress, value, payload, v, r, s, gasUsed, gasPrice, blockNumber, blockHash, location) values ($holes)"
    Update[(String, Int, String, String, String, String, String, String, String, String, String, Long, String, Int)](
      sql)
      .updateMany(xs)
      .transact(xa)
      .void
  }
}
