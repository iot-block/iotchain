package jbok.core.sync

import cats.effect.ConcurrentEffect
import cats.implicits._
import fs2._
import fs2.concurrent.SignallingRef
import jbok.core.ledger.BlockExecutor
import jbok.core.ledger.BlockImportResult._
import jbok.core.messages.{GetBlockHeaders, Message, NewBlock, NewBlockHashes}
import jbok.core.models.Block
import jbok.core.peer.PeerManager
import jbok.core.pool.{OmmerPool, TxPool}

case class Synchronizer[F[_]](
    peerManager: PeerManager[F],
    executor: BlockExecutor[F],
    txPool: TxPool[F],
    ommerPool: OmmerPool[F],
    broadcaster: Broadcaster[F],
    stopWhenTrue: SignallingRef[F, Boolean]
)(implicit F: ConcurrentEffect[F]) {
  private[this] val log = org.log4s.getLogger

  val history = executor.history

  def stream: Stream[F, Unit] =
    peerManager.subscribe
      .evalMap {
        case (peer, NewBlock(block)) =>
          log.info(s"received NewBlock(${block.tag}) from ${peer.conn.remoteAddress}")

          for {
            _            <- peer.knownBlock(block.header.hash)
            importResult <- executor.importBlock(block)
            _ = log.info(s"${peerManager.config.bindAddr}: import block result: ${importResult}")
            _ <- importResult match {
              case Succeed(newBlocks, _) =>
                log.info(s"Added new ${block.tag} to the top of the chain received from ${peer.id}")
                broadcastBlocks(newBlocks) *> updateTxAndOmmerPools(newBlocks, Nil)

              case Pooled =>
                F.delay(log.info(s"pooled ${block.tag}"))

              case Failed(error) =>
                F.delay(log.info(s"importing block error: ${error}"))
            }
          } yield ()

        case (peer, NewBlockHashes(hashes)) =>
          val request = GetBlockHeaders(Right(hashes.head.hash), hashes.length, 0, reverse = false)
          peer.conn.write[Message](request)

        case _ => F.unit
      }
      .onFinalize(stopWhenTrue.set(true) *> F.delay(log.info(s"Synchronizer stopped")))

  def handleMinedBlock(block: Block): F[Unit] =
    executor.importBlock(block).flatMap {
      case Succeed(newBlocks, _) =>
        log.info(s"add mined ${block.tag} to the main chain")
        broadcastBlocks(newBlocks) *> updateTxAndOmmerPools(newBlocks, Nil)

      case Pooled =>
        F.delay(log.info(s"add mined ${block.tag} to the pool"))

      case Failed(e) =>
        F.delay(log.error(e)(s"add mined ${block.tag} execution error"))
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
    blocks.traverse(block => broadcaster.broadcastBlock(NewBlock(block))).void
}

object Synchronizer {
  def apply[F[_]](
      peerManager: PeerManager[F],
      executor: BlockExecutor[F],
      txPool: TxPool[F],
      ommerPool: OmmerPool[F],
      broadcaster: Broadcaster[F],
  )(implicit F: ConcurrentEffect[F]): F[Synchronizer[F]] =
    for {
      s <- SignallingRef[F, Boolean](true)
    } yield Synchronizer(peerManager, executor, txPool, ommerPool, broadcaster, s)
}
