package jbok.app.service

import cats.effect.Sync
import cats.implicits._
import jbok.app.service.store.TransactionStore
import jbok.common.math.N
import jbok.core.config.HistoryConfig
import jbok.core.ledger.History
import jbok.core.models.{Account, Address, SignedTransaction}
import jbok.core.pool.TxPool
import jbok.core.api.{AccountAPI, BlockTag, HistoryTransaction}
import scodec.bits.ByteVector

final class AccountService[F[_]](config: HistoryConfig, history: History[F], txPool: TxPool[F], helper: ServiceHelper[F], txStore: TransactionStore[F])(implicit F: Sync[F])
    extends AccountAPI[F] {

  override def getAccount(address: Address, tag: BlockTag): F[Account] =
    for {
      account <- helper.resolveAccount(address, tag)
    } yield account

  override def getCode(address: Address, tag: BlockTag): F[ByteVector] =
    for {
      block <- helper.resolveBlock(tag)
      world <- history.getWorldState(config.accountStartNonce, block.map(_.header.stateRoot))
      code  <- world.getCode(address)
    } yield code

  override def getBalance(address: Address, tag: BlockTag): F[N] =
    for {
      account <- helper.resolveAccount(address, tag)
    } yield account.balance.toN

  override def getStorageAt(address: Address, position: N, tag: BlockTag): F[ByteVector] =
    for {
      account <- helper.resolveAccount(address, tag)
      storage <- history.getStorage(account.storageRoot, position)
    } yield storage

  override def getTransactions(address: Address, page: Int, size: Int): F[List[HistoryTransaction]] = {
    val validSize = if (size < 0) 100 else 1000000.min(size)
    txStore.findTransactionsByAddress(address.toString, page.max(1), validSize)
  }

  override def getTransactionsByNumber(number: Int): F[List[HistoryTransaction]] =
    txStore.findTransactionsByNumber(number)

  override def getPendingTxs(address: Address): F[List[SignedTransaction]] =
    txPool.getPendingTransactions.map(_.keys.toList.filter(_.senderAddress.exists(_ == address)))

  override def getEstimatedNonce(address: Address): F[N] =
    for {
      pending <- txPool.getPendingTransactions
      latestNonceOpt = scala.util
        .Try(pending.collect {
          case (stx, _) if stx.senderAddress contains address => stx.nonce
        }.max)
        .toOption
      bn              <- history.getBestBlockNumber
      currentNonceOpt <- history.getAccount(address, bn).map(_.map(_.nonce.toN))
      defaultNonce     = config.accountStartNonce.toN
      maybeNextTxNonce = latestNonceOpt.map(_ + 1).getOrElse(defaultNonce) max currentNonceOpt.getOrElse(defaultNonce)
    } yield maybeNextTxNonce
}
