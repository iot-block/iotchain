package jbok.core.pool

import cats.effect._
import cats.implicits._
import fs2._
import fs2.async.Ref
import fs2.async.mutable.Signal
import jbok.core.messages.SignedTransactions
import jbok.core.models.SignedTransaction
import jbok.core.peer.{PeerEvent, PeerId, PeerManager}
import scodec.bits.ByteVector

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, _}

case class PendingTransaction(stx: SignedTransaction, addTimestamp: Long)

case class TxPoolConfig(
    poolSize: Int = 1000,
    transactionTimeout: FiniteDuration = 2.minutes
)

case class TxPool[F[_]](
    pending: Ref[F, List[PendingTransaction]],
    known: Ref[F, Map[ByteVector, Set[PeerId]]],
    stopWhenTrue: Signal[F, Boolean],
    timeouts: Ref[F, Map[ByteVector, Fiber[F, Unit]]],
    peerManager: PeerManager[F],
    config: TxPoolConfig
)(implicit F: ConcurrentEffect[F], EC: ExecutionContext, T: Timer[F]) {
  private[this] val log = org.log4s.getLogger

  def stream: Stream[F, Unit] =
    peerManager
      .subscribe()
      .evalMap {
        case PeerEvent.PeerRecv(peerId, SignedTransactions(txs)) =>
          log.info(s"received ${txs.length} stxs from ${peerId}")
          handleReceived(peerId, txs)

        case PeerEvent.PeerAdd(peerId) =>
          getPendingTransactions.flatMap(xs => {
            if (xs.nonEmpty) {
              log.info(s"notify pending txs to new peer ${peerId}")
              notifyPeer(peerId, xs.map(_.stx))
            } else {
              F.unit
            }
          })

        case _ => F.unit
      }
      .onFinalize(stopWhenTrue.set(true) *> F.delay(log.info(s"stop TxPool")))

  def start: F[Unit] =
    for {
      _ <- stopWhenTrue.set(false)
      _ <- F.start(stream.interruptWhen(stopWhenTrue).compile.drain).void
      _ <- F.delay(log.info(s"start TxPool"))
    } yield ()

  def stop: F[Unit] = stopWhenTrue.set(true)

  def handleReceived(peerId: PeerId, stxs: List[SignedTransaction]): F[Unit] =
    for {
      _ <- addTransactions(stxs)
      _ <- stxs.traverse(setTxKnown(_, peerId))
    } yield ()

  def addTransactions(signedTransactions: List[SignedTransaction]): F[Unit] =
    for {
      p <- pending.get
      toAdd = signedTransactions.filterNot(t => p.map(_.stx).contains(t))
      _ <- if (toAdd.isEmpty) {
        log.info(s"ignore ${signedTransactions.length} knwon stxs")
        F.unit
      } else {
        log.info(s"add ${toAdd.length} pending stxs")
        val timestamp = System.currentTimeMillis()
        for {
          _     <- toAdd.traverse(setTimeout)
          _     <- pending.modify(xs => (toAdd.map(PendingTransaction(_, timestamp)) ++ xs).take(config.poolSize))
          peers <- peerManager.handshakedPeers
          _     <- peers.keys.toList.traverse(peerId => notifyPeer(peerId, toAdd))
        } yield ()
      }
    } yield ()

  def addOrUpdateTransaction(newStx: SignedTransaction) =
    for {
      _ <- setTimeout(newStx)
      p <- pending.get
      (a, b) = p.partition(
        tx =>
          tx.stx.senderAddress(Some(0x3d.toByte)) == newStx
            .senderAddress(Some(0x3d.toByte)) && tx.stx.nonce == newStx.nonce)
      _ <- a.traverse(x => clearTimeout(x.stx))
      timestamp = System.currentTimeMillis()
      _     <- pending.modify(_ => (PendingTransaction(newStx, timestamp) +: b).take(config.poolSize))
      peers <- peerManager.handshakedPeers
      _     <- peers.keys.toList.traverse(peerId => notifyPeer(peerId, newStx :: Nil))
    } yield ()

  def removeTransactions(signedTransactions: List[SignedTransaction]): F[Unit] =
    for {
      _ <- pending.modify(_.filterNot(x => signedTransactions.contains(x.stx)))
      _ <- known.modify(_.filterNot(x => signedTransactions.map(_.hash).contains(x._1)))
      _ <- signedTransactions.traverse(clearTimeout)
    } yield ()

  def getPendingTransactions: F[List[PendingTransaction]] =
    pending.get

  def notifyPeer(peerId: PeerId, stxs: List[SignedTransaction]): F[Unit] =
    for {
      p <- pending.get
      k <- known.get
      toNotify = stxs
        .filter(stx => p.exists(_.stx.hash == stx.hash))
        .filterNot(stx => k.getOrElse(stx.hash, Set.empty).contains(peerId))
      _ <- if (toNotify.isEmpty) {
        log.info(s"transactions already known")
        F.unit
      } else {
        log.info(s"notify ${toNotify.length} transactions to peers")
        peerManager.sendMessage(peerId, SignedTransactions(toNotify)) *>
          toNotify.traverse(setTxKnown(_, peerId))
      }
    } yield ()

  private def setTxKnown(stx: SignedTransaction, peerId: PeerId): F[Unit] =
    known.modify(_ |+| Map(stx.hash -> Set(peerId))).void

  private def setTimeout(stx: SignedTransaction): F[Unit] =
    for {
      _     <- clearTimeout(stx)
      fiber <- F.start(T.sleep(config.transactionTimeout) *> removeTransactions(stx :: Nil))
      _     <- timeouts.modify(_ + (stx.hash -> fiber))
    } yield ()

  private def clearTimeout(stx: SignedTransaction): F[Unit] =
    for {
      m <- timeouts.get
      _ <- m.get(stx.hash) match {
        case None    => F.unit
        case Some(f) => f.cancel *> timeouts.modify(_ - stx.hash)
      }
    } yield ()
}

object TxPool {
  def apply[F[_]](peerManager: PeerManager[F], config: TxPoolConfig)(
      implicit F: ConcurrentEffect[F],
      EC: ExecutionContext,
      T: Timer[F]
  ): F[TxPool[F]] =
    for {
      pending      <- fs2.async.refOf[F, List[PendingTransaction]](Nil)
      known        <- fs2.async.refOf[F, Map[ByteVector, Set[PeerId]]](Map.empty)
      stopWhenTrue <- fs2.async.signalOf[F, Boolean](true)
      timeouts     <- fs2.async.refOf[F, Map[ByteVector, Fiber[F, Unit]]](Map.empty)
    } yield TxPool(pending, known, stopWhenTrue, timeouts, peerManager, config)
}
