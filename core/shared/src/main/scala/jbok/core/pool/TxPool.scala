package jbok.core.pool

import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import jbok.core.config.Configs.TxPoolConfig
import jbok.core.messages.{Message, SignedTransactions}
import jbok.core.models.SignedTransaction
import jbok.core.peer._

import scala.concurrent.duration._

final case class PendingTransaction(stx: SignedTransaction, addTimestamp: Long)

final case class TxPool[F[_]](
    config: TxPoolConfig,
    pending: Ref[F, List[PendingTransaction]],
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  private[this] val log = org.log4s.getLogger("TxPool")

  val service: PeerRoutes[F] = PeerRoutes.of[F] {
    case Request(peer, peerSet, SignedTransactions(txs)) =>
      log.debug(s"received ${txs.length} stxs from ${peer.id}")
      for {
        _      <- txs.traverse(stx => peer.markTx(stx.hash))
        result <- addTransactions(txs, peerSet)
      } yield result
  }

  def addTransactions(signedTransactions: List[SignedTransaction],
                      peerSet: PeerSet[F] = PeerSet.empty[F]): F[List[(Peer[F], Message)]] =
    for {
      p <- pending.get
      toAdd = signedTransactions.filterNot(t => p.map(_.stx).contains(t))
      current <- T.clock.realTime(MILLISECONDS)
      _       <- pending.update(xs => (toAdd.map(PendingTransaction(_, current)) ++ xs).take(config.poolSize))
      xs      <- peerSet.connected.flatMap(_.traverse(peer => notification(peer, toAdd)).map(_.flatten))
    } yield xs

  def addOrUpdateTransaction(newStx: SignedTransaction, peerSet: PeerSet[F] = PeerSet.empty[F]): F[Unit] =
    for {
      p <- pending.get
      (a, b) = p.partition(
        tx =>
          tx.stx.senderAddress(None) == newStx.senderAddress(None) && tx.stx.nonce == newStx.nonce)
      current <- T.clock.realTime(MILLISECONDS)
      _       <- pending.set((PendingTransaction(newStx, current) +: b).take(config.poolSize))
      _       <- peerSet.connected.flatMap(_.traverse(peer => notification(peer, newStx :: Nil)))
    } yield ()

  def removeTransactions(signedTransactions: List[SignedTransaction]): F[Unit] =
    for {
      _ <- pending.update(_.filterNot(x => signedTransactions.contains(x.stx)))
    } yield ()

  def getPendingTransactions: F[List[PendingTransaction]] =
    for {
      current <- T.clock.realTime(MILLISECONDS)
      txs     <- pending.get
      alive = txs.filter(x => current - x.addTimestamp <= config.transactionTimeout.toMillis)
      _ <- pending.set(alive)
    } yield alive

  def notification(peer: Peer[F], stxs: List[SignedTransaction]): F[Option[(Peer[F], Message)]] =
    for {
      p <- pending.get
      toNotify <- stxs
        .filter(stx => p.exists(_.stx.hash == stx.hash))
        .traverse(stx =>
          peer.hasTx(stx.hash).map {
            case true  => None
            case false => Some(stx)
        })
        .map(_.flatten)
      _ <- toNotify.traverse(stx => peer.markTx(stx.hash))
      message = if (toNotify.isEmpty) None else Some(peer -> SignedTransactions(toNotify))
    } yield message
}

object TxPool {
  def apply[F[_]](config: TxPoolConfig = TxPoolConfig())(
      implicit F: ConcurrentEffect[F],
      T: Timer[F]
  ): F[TxPool[F]] =
    for {
      pending <- Ref.of[F, List[PendingTransaction]](Nil)
    } yield TxPool(config, pending)
}
