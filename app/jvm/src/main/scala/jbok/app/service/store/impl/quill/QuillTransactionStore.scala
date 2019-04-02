package jbok.app.service.store.impl.quill

import cats.effect.{ConcurrentEffect, IO}
import cats.implicits._
import jbok.app.service.models.Transaction
import jbok.app.service.store.TransactionStore
import jbok.core.models.{Receipt, Block => CoreBlock}
import monix.eval.Task

@SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
class QuillTransactionStore(ctx: Quill.Ctx)(implicit F: ConcurrentEffect[Task]) extends TransactionStore[IO] {
  import ctx._

  private val Transactions = quote(querySchema[Transaction]("transactions"))

  override def findAllTxs(page: Int, size: Int): IO[List[Transaction]] = {
    val q = quote(Transactions.drop(lift((page - 1) * size)).take(lift(size)))
    ctx.run(q).toIO
  }

  override def findTransactionsByAddress(address: String, page: Int, size: Int): IO[List[Transaction]] = {
    val q = quote(
      Transactions
        .filter(x => x.fromAddress == lift(address) || x.toAddress == lift(address))
        .drop(lift((page - 1) * size))
        .take(lift(size))
    )

    ctx.run(q).toIO
  }

  override def findTransactionByHash(txHash: String): IO[Option[Transaction]] = {
    val q = quote(Transactions.filter(_.txHash == lift(txHash)))
    ctx.run(q).toIO.map(_.headOption)
  }

  override def insertBlockTransactions(block: CoreBlock, receipts: List[Receipt]): IO[Unit] = {
    val xs = block.body.transactionList.zip(receipts).zipWithIndex.map {
      case ((stx, receipt), idx) =>
        val tx = Transaction(
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
        tx
    }

    val q = quote {
      liftQuery(xs).foreach(x => Transactions.insert(x))
    }
    ctx.run(q).toIO.void
  }
}
