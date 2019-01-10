package jbok.core.pool

import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import jbok.core.config.Configs.TxPoolConfig
import jbok.core.messages.SignedTransactions
import jbok.core.models.SignedTransaction
import jbok.core.peer._
import jbok.core.validators.TxValidator

import scala.concurrent.duration._

final class TxPool[F[_]] private (
    val config: TxPoolConfig,
    val peerManager: PeerManager[F],
    private val pending: Ref[F, Map[SignedTransaction, Long]],
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  private[this] val log = jbok.common.log.getLogger("TxPool")

  def addTransactions(stxs: SignedTransactions, notify: Boolean = false): F[Unit] =
    for {
      _       <- F.delay(log.debug(s"add ${stxs.txs.length} txs"))
      current <- T.clock.realTime(MILLISECONDS)
      _ <- pending.update { txs =>
        val updated = txs ++ stxs.txs.map(_ -> current)
        if (updated.size <= config.poolSize) {
          updated
        } else {
          updated.toArray.sortBy(-_._2).take(config.poolSize).toMap
        }
      }
      _ <- if (notify) broadcast(stxs) else F.unit
    } yield ()

  def addOrUpdateTransaction(newStx: SignedTransaction): F[Unit] =
    for {
      _       <- TxValidator.checkSyntacticValidity(newStx, peerManager.history.chainId)
      current <- T.clock.realTime(MILLISECONDS)
      _ <- pending.update { txs =>
        val (_, b) = txs.partition {
          case (tx, time) =>
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
      log.debug(s"remove ${signedTransactions.length} txs")
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
    peerManager.distribute(PeerSelectStrategy.withoutTxs(stxs), stxs)
}

object TxPool {
  def apply[F[_]](
      config: TxPoolConfig,
      peerManager: PeerManager[F]
  )(
      implicit F: ConcurrentEffect[F],
      T: Timer[F]
  ): F[TxPool[F]] =
    for {
      pending <- Ref.of[F, Map[SignedTransaction, Long]](Map.empty)
    } yield new TxPool(config, peerManager, pending)
}
