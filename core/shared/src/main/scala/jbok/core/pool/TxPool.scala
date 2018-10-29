package jbok.core.pool

import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2._
import fs2.concurrent.SignallingRef
import jbok.core.messages.{Message, SignedTransactions}
import jbok.core.models.SignedTransaction
import jbok.core.peer.{Peer, PeerManager}
import scodec.bits.ByteVector

import scala.concurrent.duration.{FiniteDuration, _}

case class PendingTransaction(stx: SignedTransaction, addTimestamp: Long)

case class TxPoolConfig(
    poolSize: Int = 1000,
    transactionTimeout: FiniteDuration = 2.minutes
)

case class TxPool[F[_]](
    pending: Ref[F, List[PendingTransaction]],
    stopWhenTrue: SignallingRef[F, Boolean],
    timeouts: Ref[F, Map[ByteVector, Fiber[F, Unit]]],
    peerManager: PeerManager[F],
    config: TxPoolConfig
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  private[this] val log = org.log4s.getLogger

  def stream: Stream[F, Unit] =
    peerManager.subscribe
      .evalMap {
        case (peer, SignedTransactions(txs)) =>
          log.info(s"received ${txs.length} stxs from ${peer.id}")
          handleReceived(peer, txs)

//        case PeerEvent.PeerAdd(peerId) =>
//          getPendingTransactions.flatMap(xs => {
//            if (xs.nonEmpty) {
//              log.info(s"notify pending txs to new peer ${peerId}")
//              notifyPeer(peerId, xs.map(_.stx))
//            } else {
//              F.unit
//            }
//          })

        case _ => F.unit
      }
      .onFinalize(stopWhenTrue.set(true) *> F.delay(log.info(s"stop TxPool")))

  def start: F[Unit] =
    stopWhenTrue.get.flatMap {
      case false =>
        F.unit
      case true =>
        stopWhenTrue.set(false) *> F.start(stream.interruptWhen(stopWhenTrue).compile.drain).void
    }

  def stop: F[Unit] = stopWhenTrue.set(true)

  def handleReceived(peer: Peer[F], stxs: List[SignedTransaction]): F[Unit] =
    for {
      _ <- addTransactions(stxs)
      _ <- stxs.traverse(stx => peer.knownTx(stx.hash))
    } yield ()

  def addTransactions(signedTransactions: List[SignedTransaction]): F[Unit] =
    for {
      p <- pending.get
      toAdd = signedTransactions.filterNot(t => p.map(_.stx).contains(t))
      _ <- if (toAdd.isEmpty) {
        log.info(s"ignore ${signedTransactions.length} known stxs")
        F.unit
      } else {
        log.info(s"add ${toAdd.length} pending stxs")
        val timestamp = System.currentTimeMillis()
        for {
          _     <- toAdd.traverse(setTimeout)
          _     <- pending.update(xs => (toAdd.map(PendingTransaction(_, timestamp)) ++ xs).take(config.poolSize))
          peers <- peerManager.connected
          _     <- peers.traverse(peerId => notifyPeer(peerId, toAdd))
        } yield ()
      }
    } yield ()

  def addOrUpdateTransaction(newStx: SignedTransaction): F[Unit] =
    for {
      _ <- setTimeout(newStx)
      p <- pending.get
      (a, b) = p.partition(
        tx =>
          tx.stx.senderAddress(Some(0x3d.toByte)) == newStx
            .senderAddress(Some(0x3d.toByte)) && tx.stx.nonce == newStx.nonce)
      _ <- a.traverse(x => clearTimeout(x.stx))
      _ = println(a.length, b.length)
      timestamp = System.currentTimeMillis()
      _     <- pending.set((PendingTransaction(newStx, timestamp) +: b).take(config.poolSize))
      peers <- peerManager.connected
      _ = log.info(s"notify ${peers.length} peer(s)")
      _ <- peers.traverse(peer => notifyPeer(peer, newStx :: Nil))
    } yield ()

  def removeTransactions(signedTransactions: List[SignedTransaction]): F[Unit] =
    for {
      _ <- pending.update(_.filterNot(x => signedTransactions.contains(x.stx)))
      _ <- signedTransactions.traverse(clearTimeout)
    } yield ()

  def getPendingTransactions: F[List[PendingTransaction]] =
    pending.get

  def notifyPeer(peer: Peer[F], stxs: List[SignedTransaction]): F[Unit] =
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
      _ <- if (toNotify.isEmpty) {
        F.unit
      } else {
        peer.conn.write[Message](SignedTransactions(toNotify)) *> toNotify.traverse(stx => peer.knownTx(stx.hash))
      }
    } yield ()

  private def setTimeout(stx: SignedTransaction): F[Unit] =
    for {
      _     <- clearTimeout(stx)
      fiber <- F.start(T.sleep(config.transactionTimeout) *> removeTransactions(stx :: Nil))
      _     <- timeouts.update(_ + (stx.hash -> fiber))
    } yield ()

  private def clearTimeout(stx: SignedTransaction): F[Unit] =
    for {
      m <- timeouts.get
      _ <- m.get(stx.hash) match {
        case None    => F.unit
        case Some(f) => timeouts.update(_ - stx.hash) *> f.cancel
      }
    } yield ()
}

object TxPool {
  def apply[F[_]](peerManager: PeerManager[F], config: TxPoolConfig = TxPoolConfig())(
      implicit F: ConcurrentEffect[F],
      T: Timer[F]
  ): F[TxPool[F]] =
    for {
      pending      <- Ref.of[F, List[PendingTransaction]](Nil)
      stopWhenTrue <- SignallingRef[F, Boolean](true)
      timeouts     <- Ref.of[F, Map[ByteVector, Fiber[F, Unit]]](Map.empty)
    } yield TxPool(pending, stopWhenTrue, timeouts, peerManager, config)
}
