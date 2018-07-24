package jbok.core.sync

import cats.effect.ConcurrentEffect
import cats.implicits._
import fs2._
import fs2.async.mutable.Signal
import jbok.core.ledger.BlockImportResult._
import jbok.core.ledger.Ledger
import jbok.core.messages.{GetBlockHeaders, NewBlock, NewBlockHashes}
import jbok.core.models.Block
import jbok.core.peer.{PeerEvent, PeerManager}

import scala.concurrent.ExecutionContext

case class RegularSync[F[_]](
    peerManager: PeerManager[F],
    ledger: Ledger[F],
    stopWhenTrue: Signal[F, Boolean]
)(implicit F: ConcurrentEffect[F], EC: ExecutionContext) {
  private[this] val log = org.log4s.getLogger

  def stream: Stream[F, Unit] = peerManager.subscribeMessages().evalMap {
    case PeerEvent.PeerRecv(peerId, NewBlock(block)) =>
      ledger.importBlock(block).value.map {
        case Right(BlockImported(newBlocks, Nil)) =>
          broadcastBlocks(newBlocks)
          updateTxAndOmmerPools(newBlocks, Nil)
          log.debug(s"Added new block ${block.header.number} to the top of the chain received from $peerId")

        case Right(DuplicateBlock) =>
          log.debug(s"Ignoring duplicate block ${block.header.number}) from ${peerId}")

        case Right(UnknownParent) =>
          log.debug(s"Ignoring orphaned block ${block.header.number} from $peerId")

        case Right(BlockImportFailed(error)) =>
          log.info(s"importing block error: ${error}")

        case Left(error) =>
          log.warn(s"import block invalid: ${error}")
      }

    case PeerEvent.PeerRecv(peerId, NewBlockHashes(hashes)) =>
      val request = GetBlockHeaders(Right(hashes.head.hash), hashes.length, 0, reverse = false)
      peerManager.sendMessage(peerId, request)

    case _ => F.unit
  }

  def start: F[Unit] = stopWhenTrue.set(false) *> F.start(stream.interruptWhen(stopWhenTrue).compile.drain).void

  def stop: F[Unit] = stopWhenTrue.set(true)

  private def updateTxAndOmmerPools(blocksAdded: Seq[Block], blocksRemoved: Seq[Block]): Unit = {
//    blocksRemoved.headOption.foreach(block => ommersPool ! AddOmmers(block.header))
//    blocksRemoved.foreach(block => pendingTransactionsManager ! AddTransactions(block.body.transactionList.toList))
//
//    blocksAdded.foreach { block =>
//      ommersPool ! RemoveOmmers(block.header :: block.body.uncleNodesList.toList)
//      pendingTransactionsManager ! RemoveTransactions(block.body.transactionList)
//    }
  }

  private def broadcastBlocks(blocks: Seq[Block]): Unit = {
//    blocks.zip(totalDifficulties).foreach { case (block, td) =>
//      broadcaster.broadcastBlock(NewBlock(block, td), handshakedPeers)
//    }
  }
}

object RegularSync {
  def apply[F[_]](
      peerManager: PeerManager[F],
      ledger: Ledger[F]
  )(implicit F: ConcurrentEffect[F], EC: ExecutionContext): F[RegularSync[F]] =
    fs2.async
      .signalOf[F, Boolean](true)
      .map(s => RegularSync(peerManager, ledger, s))
}
