package jbok.core.pool

import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2._
import jbok.common.log.Logger
import jbok.core.config.TxPoolConfig
import jbok.core.ledger.History
import jbok.core.messages.SignedTransactions
import jbok.core.models.{Account, SignedTransaction, UInt256}
import jbok.core.peer.PeerSelector.PeerSelector
import jbok.core.peer._
import jbok.core.queue.{Consumer, Producer}
import jbok.core.validators.TxValidator

import scala.concurrent.duration._

/**
  * `TxPool` is responsible for consume transaction messages from peers
  * and produce message request for broadcasting
  */
final class TxPool[F[_]](
    config: TxPoolConfig,
    history: History[F],
    inbound: Consumer[F, Peer[F], SignedTransactions],
    outbound: Producer[F, PeerSelector[F], SignedTransactions]
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  private[this] val log = Logger[F]

  val pending: Ref[F, Map[SignedTransaction, Long]] = Ref.unsafe(Map.empty)

  def addTransactions(stxs: SignedTransactions, notify: Boolean = false): F[Unit] =
    for {
      _       <- log.debug(s"add ${stxs.txs.length} txs")
      current <- T.clock.realTime(MILLISECONDS)
      parentStateRoot <- history.getBestBlockHeader.map(_.stateRoot)
      world <- history.getWorldState(
        UInt256.zero,
        Some(parentStateRoot),
        false
      )
      filteredTxs <- stxs.txs.filterA(tx => for {
        senderAddress <- tx.getSenderOrThrow[F]
        senderAccount <- world.getAccountOpt(senderAddress).getOrElse(Account.empty(UInt256.zero))
        nonceValid = tx.nonce+20 >= senderAccount.nonce
          _ <- if (!nonceValid){
          log.d(s"ignore past transaction ${tx}, accountNonce is ${senderAccount.nonce}, but txNonce is ${tx.nonce}, nonce must be greater than ${senderAccount.nonce-20}")
        }else {
          F.unit
        }
      } yield nonceValid )
      filteredStxs = SignedTransactions(filteredTxs)

      _ <- pending.update { txs =>
        val updated = txs ++ filteredStxs.txs.map(_ -> current)
        if (updated.size <= config.poolSize) {
          updated
        } else {
          updated.toArray.sortBy(-_._2).take(config.poolSize).toMap
        }
      }
      _ <- if (notify) broadcast(filteredStxs) else F.unit
    } yield ()

  def receiveTransactions(peer: Peer[F], stxs: SignedTransactions): F[Unit] =
    peer.markTxs(stxs) >> addTransactions(stxs, notify = true)

  def reAddTransactions(stxs: SignedTransactions): F[Unit] =
    addTransactions(stxs, notify = false)

  def addOrUpdateTransaction(newStx: SignedTransaction): F[Unit] =
    for {
      _       <- TxValidator.checkSyntacticValidity(newStx, history.chainId)
      current <- T.clock.realTime(MILLISECONDS)
      _ <- pending.update { txs =>
        val (_, b) = txs.partition {
          case (tx, _) =>
            tx.senderAddress == newStx.senderAddress && tx.nonce == newStx.nonce
        }
        val updated = b + (newStx -> current)
        if (updated.size <= config.poolSize) {
          updated
        } else {
          updated.toArray.sortBy(-_._2).take(config.poolSize).toMap
        }
      }
      _ <- broadcast(SignedTransactions(newStx :: Nil))
    } yield ()

  def removeTransactions(signedTransactions: List[SignedTransaction]): F[Unit] =
    if (signedTransactions.nonEmpty) {
      log.debug(s"remove ${signedTransactions.length} txs") >>
        pending.update(_.filterNot { case (tx, _) => signedTransactions.contains(tx) })
    } else {
      F.unit
    }

  def getPendingTransactions: F[Map[SignedTransaction, Long]] =
    for {
      current <- T.clock.realTime(MILLISECONDS)
      alive <- pending.modify { txs =>
        val alive = txs.filter { case (_, time) => current - time <= config.transactionTimeout.toMillis }
        alive -> alive
      }
    } yield alive

  def broadcast(stxs: SignedTransactions): F[Unit] =
    outbound.produce(PeerSelector.withoutTxs(stxs), stxs)

  val stream: Stream[F, Unit] =
    Stream.eval_(log.i(s"starting Core/TxPool")) ++
      inbound.consume.evalMap { case (peer, stxs) => receiveTransactions(peer, stxs) }
}
