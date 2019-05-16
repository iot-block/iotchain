package jbok.app.service

import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import jbok.codec.rlp.implicits._
import jbok.core.ledger.History
import jbok.core.models.{Receipt, SignedTransaction}
import jbok.core.pool.TxPool
import jbok.core.api.{BlockTag, TransactionAPI}
import scodec.bits.ByteVector

final class TransactionService[F[_]](history: History[F], txPool: TxPool[F], helper: ServiceHelper[F])(implicit F: Sync[F]) extends TransactionAPI[F] {
  override def getTx(hash: ByteVector): F[Option[SignedTransaction]] =
    (for {
      loc   <- OptionT(history.getTransactionLocation(hash))
      block <- OptionT(history.getBlockByHash(loc.blockHash))
      stx   <- OptionT.fromOption[F](block.body.transactionList.lift(loc.txIndex))
    } yield stx).value

  override def getPendingTx(hash: ByteVector): F[Option[SignedTransaction]] =
    txPool.getPendingTransactions.map(_.keys.toList.find(_.hash == hash))

  override def getReceipt(hash: ByteVector): F[Option[Receipt]] =
    (for {
      loc      <- OptionT(history.getTransactionLocation(hash))
      block    <- OptionT(history.getBlockByHash(loc.blockHash))
      _        <- OptionT.fromOption[F](block.body.transactionList.lift(loc.txIndex))
      receipts <- OptionT(history.getReceiptsByHash(loc.blockHash))
      receipt  <- OptionT.fromOption[F](receipts.lift(loc.txIndex))
    } yield receipt).value

  override def getTxByBlockHashAndIndex(hash: ByteVector, index: Int): F[Option[SignedTransaction]] =
    (for {
      block <- OptionT(history.getBlockByHash(hash))
      stx   <- OptionT.fromOption[F](block.body.transactionList.lift(index))
    } yield stx).value

  override def getTxByBlockTagAndIndex(tag: BlockTag, index: Int): F[Option[SignedTransaction]] =
    (for {
      block <- OptionT(helper.resolveBlock(tag))
      stx   <- OptionT.fromOption[F](block.body.transactionList.lift(index))
    } yield stx).value

  override def sendTx(stx: SignedTransaction): F[ByteVector] =
    txPool.addOrUpdateTransaction(stx).as(stx.hash)

  override def sendRawTx(data: ByteVector): F[ByteVector] =
    for {
      stx <- F.fromEither(data.asEither[SignedTransaction])
      _   <- txPool.addOrUpdateTransaction(stx)
    } yield stx.hash
}
