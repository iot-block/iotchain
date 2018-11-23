package jbok.core.pool

import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import jbok.core.config.Configs.TxPoolConfig
import jbok.core.messages.{Message, SignedTransactions}
import jbok.core.models.SignedTransaction
import jbok.core.peer.PeerSelectStrategy.PeerSelectStrategy
import jbok.core.peer._

import scala.concurrent.duration._

final case class PendingTransaction(stx: SignedTransaction, addTimestamp: Long)

final case class TxPool[F[_]](
    config: TxPoolConfig,
    peerManager: PeerManager[F],
    pending: Ref[F, List[PendingTransaction]],
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  private[this] val log = org.log4s.getLogger("TxPool")

  def handleReceived(peer: Peer[F], stxs: SignedTransactions): F[List[(PeerSelectStrategy[F], Message)]] =
    for {
      p <- pending.get
      added = stxs.txs.filterNot(t => p.map(_.stx).contains(t))
      current <- T.clock.realTime(MILLISECONDS)
      _       <- pending.update(xs => (added.map(PendingTransaction(_, current)) ++ xs).take(config.poolSize))
    } yield List(PeerSelectStrategy.except(peer) -> stxs)

  def addTransactions(signedTransactions: List[SignedTransaction]): F[Unit] =
    for {
      p <- pending.get
      added = signedTransactions.filterNot(t => p.map(_.stx).contains(t))
      current <- T.clock.realTime(MILLISECONDS)
      _       <- pending.update(xs => (added.map(PendingTransaction(_, current)) ++ xs).take(config.poolSize))
      _       <- notify(added)
    } yield ()

  def addOrUpdateTransaction(newStx: SignedTransaction): F[Unit] =
    for {
      p <- pending.get
      (a, b) = p.partition(tx =>
        tx.stx.senderAddress(None) == newStx.senderAddress(None) && tx.stx.nonce == newStx.nonce)
      current <- T.clock.realTime(MILLISECONDS)
      _       <- pending.set((PendingTransaction(newStx, current) +: b).take(config.poolSize))
      _       <- notify(newStx :: Nil)
    } yield ()

  def removeTransactions(signedTransactions: List[SignedTransaction]): F[Unit] =
    pending.update(_.filterNot(x => signedTransactions.contains(x.stx)))

  def getPendingTransactions: F[List[PendingTransaction]] =
    for {
      current <- T.clock.realTime(MILLISECONDS)
      txs     <- pending.get
      alive = txs.filter(x => current - x.addTimestamp <= config.transactionTimeout.toMillis)
      _ <- pending.set(alive)
    } yield alive

  def notify(stxs: List[SignedTransaction]): F[Unit] =
    for {
      peers <- peerManager.connected
      _     <- peers.traverse(peer => notifyPeer(stxs, peer))
    } yield ()

  private def notifyPeer(stxs: List[SignedTransaction], peer: Peer[F]): F[Unit] =
    for {
      unknown <- stxs
        .traverse[F, Option[SignedTransaction]](stx =>
          peer.hasTx(stx.hash).map {
            case true  => None
            case false => Some(stx)
        })
        .map(_.flatten)
      _ <- if (unknown.isEmpty) {
        F.unit
      } else {
        peer.conn.write(SignedTransactions(unknown)) *> peer.markTxs(unknown.map(_.hash))
      }
    } yield ()
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
      pending <- Ref.of[F, List[PendingTransaction]](Nil)
    } yield TxPool(config, peerManager, pending)
}
