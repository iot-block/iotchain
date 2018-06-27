package jbok.core.sync

import cats.effect.Sync
import fs2._
import jbok.core.messages.NewBlock
import cats.implicits._
import jbok.core.ledger.BlockImportResult._
import jbok.core.ledger.Ledger
import jbok.core.models.Block
import jbok.p2p.peer.PeerId

class RegularSync[F[_]](blocks: Stream[F, (NewBlock, PeerId)], ledger: Ledger[F])(implicit F: Sync[F]) {
  private[this] val log = org.log4s.getLogger

  val start = blocks.evalMap {
    case (NewBlock(block), peerId) =>
      ledger.importBlock(block).map {
        case BlockImported(newBlocks) =>
          broadcastBlocks(newBlocks)
          updateTxAndOmmerPools(newBlocks, Nil)
          log.debug(s"Added new block ${block.header.number} to the top of the chain received from $peerId")

        case DuplicateBlock =>
          log.debug(s"Ignoring duplicate block ${block.header.number}) from ${peerId}")

        case UnknownParent =>
          log.debug(s"Ignoring orphaned block ${block.header.number} from $peerId")

        case BlockImportFailed(error) =>
          log.info(s"importing block error: ${error}")
      }
  }

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
