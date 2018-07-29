package jbok.core.sync

import cats.effect.ConcurrentEffect
import cats.implicits._
import fs2._
import fs2.async.mutable.Signal
import jbok.core.TxPool
import jbok.core.ledger.BlockImportResult._
import jbok.core.ledger.{Ledger, OmmersPool}
import jbok.core.messages.{GetBlockHeaders, NewBlock, NewBlockHashes}
import jbok.core.models.Block
import jbok.core.peer.{PeerEvent, PeerManager}

import scala.concurrent.ExecutionContext

case class RegularSync[F[_]](
    peerManager: PeerManager[F],
    ledger: Ledger[F],
    ommersPool: OmmersPool[F],
    txPool: TxPool[F],
    broadcaster: Broadcaster[F],
    stopWhenTrue: Signal[F, Boolean]
)(implicit F: ConcurrentEffect[F], EC: ExecutionContext) {
  private[this] val log = org.log4s.getLogger

  def stream: Stream[F, Unit] = peerManager.subscribeMessages().evalMap {
    case PeerEvent.PeerRecv(peerId, NewBlock(block)) =>
      log.info(s"received NewBlock")
      ledger.importBlock(block).flatMap { br =>
        log.info(s"import block result: ${br}")

        br match {
          case BlockImported(newBlocks, _) =>
            log.info(s"Added new block ${block.header.number} to the top of the chain received from $peerId")
            broadcastBlocks(newBlocks) *> updateTxAndOmmerPools(newBlocks, Nil)

          case BlockPooled =>
            F.delay(log.info(s"queued block ${block.header.number}"))

          case DuplicateBlock =>
            F.delay(log.info(s"Ignoring duplicate block ${block.header.number}) from ${peerId}"))

          case UnknownParent =>
            F.delay(log.info(s"Ignoring orphaned block ${block.header.number} from $peerId"))

          case BlockImportFailed(error) =>
            F.delay(log.info(s"importing block error: ${error}"))
        }
      }

    case PeerEvent.PeerRecv(peerId, NewBlockHashes(hashes)) =>
      val request = GetBlockHeaders(Right(hashes.head.hash), hashes.length, 0, reverse = false)
      peerManager.sendMessage(peerId, request)

    case _ => F.unit
  }

  def start: F[Unit] = stopWhenTrue.set(false) *> F.start(stream.interruptWhen(stopWhenTrue).compile.drain).void

  def stop: F[Unit] = stopWhenTrue.set(true)

  private def updateTxAndOmmerPools(blocksAdded: List[Block], blocksRemoved: List[Block]): F[Unit] = {
    log.info(s"update txPool and ommerPool with ${blocksAdded.length} ADDs and ${blocksRemoved.length} REMOVEs")
    for {
      _ <- ommersPool.addOmmers(blocksRemoved.headOption.toList.map(_.header))
      _ <- blocksRemoved.map(_.body.transactionList).traverse(txPool.addTransactions)
      _ <- blocksAdded.map { block =>
        ommersPool.removeOmmers(block.header :: block.body.uncleNodesList) *>
          txPool.removeTransactions(block.body.transactionList)
      }.sequence
    } yield ()
  }

  private def broadcastBlocks(blocks: List[Block]): F[Unit] =
    blocks.traverse(block => broadcaster.broadcastBlock(NewBlock(block))).void
}

object RegularSync {
  def apply[F[_]](
      peerManager: PeerManager[F],
      ledger: Ledger[F],
      ommersPool: OmmersPool[F],
      txPool: TxPool[F],
      broadcaster: Broadcaster[F],
  )(implicit F: ConcurrentEffect[F], EC: ExecutionContext): F[RegularSync[F]] =
    fs2.async
      .signalOf[F, Boolean](true)
      .map(s => RegularSync(peerManager, ledger, ommersPool, txPool, broadcaster, s))
}