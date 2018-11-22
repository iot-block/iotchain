package jbok.core.sync

import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import fs2._
import jbok.core.config.Configs.SyncConfig
import jbok.core.ledger.BlockExecutor
import jbok.core.ledger.TypedBlock.ReceivedBlock
import jbok.core.messages._
import jbok.core.models.Block
import jbok.core.peer._
import scodec.bits.ByteVector

import scala.util.Random

final case class SyncManager[F[_]](
    config: SyncConfig,
    peerManager: PeerManager[F],
    executor: BlockExecutor[F],
    fullSync: FullSync[F],
    fastSync: FastSync[F]
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  private[this] val log = org.log4s.getLogger("SyncManager")

  def serve: Stream[F, Unit] =
    peerManager.messageQueue.dequeue.evalMap { req =>
      log.trace(s"received request ${req.message}")
      service.run(req).flatMap { response =>
        val s: List[(Peer[F], Message)] = response
        s.traverse[F, Unit] { case (peer, message) => peer.conn.write(message) }.void
      }
    }

  val history = executor.history

  val requestService: PeerRoutes[F] = PeerRoutes.of[F] {
    case Request(peer, peerSet, GetReceipts(hashes, id)) =>
      for {
        receipts <- hashes.traverse(history.getReceiptsByHash).map(_.flatten)
      } yield peer -> Receipts(receipts, id) :: Nil

    case Request(peer, peerSet, GetBlockBodies(hashes, id)) =>
      for {
        bodies <- hashes.traverse(hash => history.getBlockBodyByHash(hash)).map(_.flatten)
      } yield peer -> BlockBodies(bodies, id) :: Nil

    case Request(peer, peerSet, GetBlockHeaders(block, maxHeaders, skip, reverse, id)) =>
      val blockNumber: F[Option[BigInt]] = block match {
        case Left(v)   => v.some.pure[F]
        case Right(bv) => history.getBlockHeaderByHash(bv).map(_.map(_.number))
      }

      blockNumber.flatMap {
        case Some(startBlockNumber) if startBlockNumber >= 0 && maxHeaders >= 0 && skip >= 0 =>
          val headersCount = math.min(maxHeaders, config.maxBlockHeadersPerRequest)

          val range = if (reverse) {
            startBlockNumber to (startBlockNumber - (skip + 1) * headersCount + 1) by -(skip + 1)
          } else {
            startBlockNumber to (startBlockNumber + (skip + 1) * headersCount - 1) by (skip + 1)
          }

          range.toList
            .traverse(history.getBlockHeaderByNumber)
            .map(_.flatten)
            .map(values => peer -> BlockHeaders(values, id) :: Nil)

        case _ =>
          F.pure(Nil)
      }

    case Request(peer, peerSet, GetNodeData(nodeHashes, id)) =>
      val nodeData = nodeHashes
        .traverse[F, Option[ByteVector]] {
          case NodeHash.StateMptNodeHash(v)           => history.getMptNode(v)
          case NodeHash.StorageRootHash(v)            => history.getMptNode(v)
          case NodeHash.ContractStorageMptNodeHash(v) => history.getMptNode(v)
          case NodeHash.EvmCodeHash(v)                => history.getCode(v)
        }
        .map(_.flatten)

      nodeData.map(values => peer -> NodeData(values, id) :: Nil)
  }

  val messageService: PeerRoutes[F] = PeerRoutes.of[F] {
    case Request(peer, peerSet, NewBlockHashes(hashes)) =>
      hashes.traverse(hash => peer.markBlock(hash.hash)) *> F.pure(Nil)

    case Request(peer, peerSet, NewBlock(block)) =>
      // we should update tx and ommer pool inside block executor
      // where and when should we broadcast block
      peer.markBlock(block.header.hash) *>
        executor.handleReceivedBlock(ReceivedBlock(block, peer, peerSet)) *>
        broadcastBlock(peerSet, block)

    case Request(peer, peerSet, SignedTransactions(txs)) =>
      log.debug(s"received ${txs.length} stxs from ${peer.id}")
      for {
        _      <- txs.traverse(stx => peer.markTx(stx.hash))
        result <- executor.txPool.addTransactions(txs, peerSet)
      } yield result
  }

  val service: PeerService[F] =
    (requestService <+> messageService).orNil

  ///////////////////////////////////
  ///////////////////////////////////

  private def broadcastBlock(peerSet: PeerSet[F], block: Block): F[List[(Peer[F], Message)]] =
    for {
      peers <- peerSet.peersWithoutBlock(block)
      xs1   <- broadcastNewBlockHash(block, peers)
      xs2   <- broadcastNewBlock(block, peers)
    } yield xs1 ++ xs2

  private def broadcastNewBlockHash(block: Block, peers: List[Peer[F]]): F[List[(Peer[F], Message)]] = {
    log.debug(s"broadcast NewBlockHashes to ${peers.length} peer(s)")
    peers
      .map { peer =>
        val newBlockHeader = block.header
        val newBlockHashes = NewBlockHashes(BlockHash(newBlockHeader.hash, newBlockHeader.number) :: Nil)
        peer -> newBlockHashes.asInstanceOf[Message]
      }
      .pure[F]
  }

  private def broadcastNewBlock(block: Block, peers: List[Peer[F]]): F[List[(Peer[F], Message)]] = {
    val selected: List[Peer[F]] = randomSelectPeers(peers)
    log.debug(s"broadcast NewBlock to random selected ${selected.size} peer(s)")
    selected.map(peer => peer -> NewBlock(block).asInstanceOf[Message]).pure[F]
  }

  private def randomSelectPeers(peers: List[Peer[F]]): List[Peer[F]] = {
    val numberOfPeersToSend = math.max(math.sqrt(peers.size).toInt, config.minBroadcastPeers)
    Random.shuffle(peers).take(numberOfPeersToSend)
  }

}

object SyncManager {
  def apply[F[_]](
      config: SyncConfig,
      peerManager: PeerManager[F],
      executor: BlockExecutor[F],
  )(implicit F: ConcurrentEffect[F], T: Timer[F]): F[SyncManager[F]] =
    for {
      fastSync <- FastSync[F](config, peerManager)
      fullSync = FullSync[F](config, peerManager, executor)
    } yield SyncManager[F](config, peerManager, executor, fullSync, fastSync)
}
