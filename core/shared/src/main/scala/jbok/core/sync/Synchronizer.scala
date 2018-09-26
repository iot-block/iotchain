package jbok.core.sync

import cats.effect.ConcurrentEffect
import cats.implicits._
import fs2._
import fs2.async.Ref
import fs2.async.mutable.Signal
import jbok.core.ledger.BlockExecutor
import jbok.core.ledger.BlockImportResult._
import jbok.core.messages.{GetBlockHeaders, NewBlock, NewBlockHashes}
import jbok.core.models.Block
import jbok.core.peer.{PeerEvent, PeerId, PeerManager}
import jbok.core.pool.{OmmerPool, TxPool}
import scodec.bits.ByteVector

import scala.concurrent.ExecutionContext

case class Synchronizer[F[_]](
    peerManager: PeerManager[F],
    executor: BlockExecutor[F],
    txPool: TxPool[F],
    ommerPool: OmmerPool[F],
    broadcaster: Broadcaster[F],
    peerHasBlock: Ref[F, Map[PeerId, Set[ByteVector]]],
    stopWhenTrue: Signal[F, Boolean]
)(implicit F: ConcurrentEffect[F], EC: ExecutionContext) {
  private[this] val log = org.log4s.getLogger(EC.toString)

  val history = executor.history

  def stream: Stream[F, Unit] =
    peerManager
      .subscribe()
      .evalMap {
        case PeerEvent.PeerRecv(peerId, NewBlock(block)) =>
          log.info(s"received NewBlock(${block.header.number})#(${block.header.hash.toHex.take(7)}))")

          for {
            peerHasBlockMap <- peerHasBlock.get
            blockSet = peerHasBlockMap.getOrElse(peerId, Set.empty)
            _            <- peerHasBlock.modify(_ + (peerId -> (blockSet + block.header.hash)))
            importResult <- executor.importBlock(block)
            _ = log.info(s"${peerManager.localAddress}: import block result: ${importResult}")
            _ <- importResult match {
              case Succeed(newBlocks, _) =>
                log.info(s"Added new block ${block.header.number} to the top of the chain received from $peerId")
                broadcastBlocks(newBlocks) *> updateTxAndOmmerPools(newBlocks, Nil)

              case Pooled =>
                F.delay(log.info(s"queued block ${block.header.number}"))

              case Failed(error) =>
                F.delay(log.info(s"importing block error: ${error}"))
            }
          } yield ()

        case PeerEvent.PeerRecv(peerId, NewBlockHashes(hashes)) =>
          val request = GetBlockHeaders(Right(hashes.head.hash), hashes.length, 0, reverse = false)
          peerManager.sendMessage(peerId, request)

        case _ => F.unit
      }
      .onFinalize(stopWhenTrue.set(true) *> F.delay(log.info(s"stop RegularSync")))

  def handleMinedBlock(block: Block): F[Unit] =
    executor.importBlock(block).flatMap {
      case Succeed(newBlocks, _) =>
        log.info(s"add mined block(${block.header.number})#(${block.header.hash.toHex.take(7)}) to the main chain")
        broadcastBlocks(newBlocks) *> updateTxAndOmmerPools(newBlocks, Nil)

      case Pooled =>
        F.delay(log.info(s"mined block added to the pool"))

      case Failed(e) =>
        F.delay(log.error(e)("mined block execution error"))
    }

  def start: F[Unit] =
    for {
      _ <- stopWhenTrue.set(false)
      _ <- F.start(stream.interruptWhen(stopWhenTrue).compile.drain).void
      _ <- F.delay(log.info("start RegularSync"))
    } yield ()

  def stop: F[Unit] = stopWhenTrue.set(true)

  private def updateTxAndOmmerPools(blocksAdded: List[Block], blocksRemoved: List[Block]): F[Unit] = {
    log.info(s"update txPool and ommerPool with ${blocksAdded.length} ADDs and ${blocksRemoved.length} REMOVEs")
    for {
      _ <- ommerPool.addOmmers(blocksRemoved.headOption.toList.map(_.header))
      _ <- blocksRemoved.map(_.body.transactionList).traverse(txPool.addTransactions)
      _ <- blocksAdded.map { block =>
        ommerPool.removeOmmers(block.header :: block.body.uncleNodesList) *>
          txPool.removeTransactions(block.body.transactionList)
      }.sequence
    } yield ()
  }

  private def broadcastBlocks(blocks: List[Block]): F[Unit] =
    blocks.traverse(block => broadcaster.broadcastBlock(NewBlock(block), peerHasBlock.get)).void
}

object Synchronizer {
  def apply[F[_]](
      peerManager: PeerManager[F],
      executor: BlockExecutor[F],
      txPool: TxPool[F],
      ommerPool: OmmerPool[F],
      broadcaster: Broadcaster[F],
  )(implicit F: ConcurrentEffect[F], EC: ExecutionContext): F[Synchronizer[F]] =
    for {
      s     <- fs2.async.signalOf[F, Boolean](true)
      peers <- fs2.async.refOf[F, Map[PeerId, Set[ByteVector]]](Map.empty)
    } yield Synchronizer(peerManager, executor, txPool, ommerPool, broadcaster, peers, s)
//  fs2.async
//      .signalOf[F, Boolean](true)
//      .map(s => Synchronizer(peerManager, executor, txPool, ommerPool, broadcaster, s))
}
