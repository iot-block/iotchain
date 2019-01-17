package jbok.core.sync

import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import fs2._
import jbok.common.metrics.Metrics
import jbok.core.config.Configs.SyncConfig
import jbok.core.ledger.BlockExecutor
import jbok.core.ledger.TypedBlock.ReceivedBlock
import jbok.core.messages._
import jbok.core.models.Block
import jbok.core.peer.PeerSelectStrategy.PeerSelectStrategy
import jbok.core.peer._
import jbok.network.{Message, Request, Response}
import scodec.bits.ByteVector
import jbok.codec.rlp.implicits._

final case class SyncManager[F[_]] private (
    config: SyncConfig,
    executor: BlockExecutor[F],
    syncStatus: Ref[F, SyncStatus],
    fullSync: FullSync[F],
    fastSync: FastSync[F]
)(implicit F: ConcurrentEffect[F], T: Timer[F], M: Metrics[F]) {
  private[this] val log = jbok.common.log.getLogger("SyncManager")

  val peerManager = executor.peerManager

  def serve: Stream[F, Unit] =
    peerManager.messageQueue.dequeue.evalMap { req =>
      log.trace(s"received request ${req.message}")
      M.timeF("peer_message") {
        service.run(req).flatMap { response: List[(PeerSelectStrategy[F], Message[F])] =>
          response
            .traverse_[F, Unit] {
              case (strategy, message) =>
                peerManager.distribute(strategy, message)
            }
        }
      }
    }

  def sync: Stream[F, Block] =
    if (!config.fastEnabled) {
      fullSync.stream
    } else {
      fastSync.stream.drain ++ fullSync.stream
    }

  val history = executor.history

  val requestService: PeerRoutes[F] = PeerRoutes.of[F] {
    case PeerRequest(peer, req @ Request(id, "GetReceipts", _)) =>
      for {
        request  <- req.bodyAs[GetReceipts]
        receipts <- request.blockHashes.traverse(history.getReceiptsByHash).map(_.flatten)
        resp     <- Response.ok[F, Receipts](id, Receipts(receipts))
      } yield PeerSelectStrategy.one(peer) -> resp :: Nil

    case PeerRequest(peer, req @ Request(id, "GetBlockBodies", _)) =>
      for {
        request <- req.bodyAs[GetBlockBodies]
        bodies  <- request.hashes.traverse(hash => history.getBlockBodyByHash(hash)).map(_.flatten)
        resp    <- Response.ok[F, BlockBodies](id, BlockBodies(bodies))
      } yield PeerSelectStrategy.one(peer) -> resp :: Nil

    case PeerRequest(peer, req @ Request(id, "GetBlockHeaders", _)) =>
      for {
        GetBlockHeaders(block, maxHeaders, skip, reverse) <- req.bodyAs[GetBlockHeaders]
        blockNumber <- block match {
          case Left(v)   => v.some.pure[F]
          case Right(bv) => history.getBlockHeaderByHash(bv).map(_.map(_.number))
        }
        res <- blockNumber match {
          case Some(startBlockNumber) if startBlockNumber >= 0 && maxHeaders >= 0 && skip >= 0 =>
            val headersCount = math.min(maxHeaders, config.maxBlockHeadersPerRequest)

            val range = if (reverse) {
              startBlockNumber to (startBlockNumber - (skip + 1) * headersCount + 1) by -(skip + 1)
            } else {
              startBlockNumber to (startBlockNumber + (skip + 1) * headersCount - 1) by (skip + 1)
            }

            for {
              values <- range.toList
                .traverse(history.getBlockHeaderByNumber)
                .map(_.flatten)
              resp <- Response.ok[F, BlockHeaders](id, BlockHeaders(values))
            } yield PeerSelectStrategy.one(peer) -> resp :: Nil

          case _ =>
            F.pure(Nil)
        }
      } yield res

    case PeerRequest(peer, req @ Request(id, "GetNodeData", _)) =>
      for {
        request <- req.bodyAs[GetNodeData]
        nodeData <- request.nodeHashes
          .traverse[F, Option[ByteVector]] {
            case NodeHash.StateMptNodeHash(v)   => history.getMptNode(v)
            case NodeHash.StorageMptNodeHash(v) => history.getMptNode(v)
            case NodeHash.EvmCodeHash(v)        => history.getCode(v)
          }
          .map(_.flatten)
        resp <- Response.ok[F, NodeData](id, NodeData(nodeData))
      } yield PeerSelectStrategy.one(peer) -> resp :: Nil
  }

  val messageService: PeerRoutes[F] = PeerRoutes.of[F] {
    case PeerRequest(peer, req @ Request(_, "NewBlockHashes", _)) =>
      for {
        request <- req.bodyAs[NewBlockHashes]
        _       <- request.hashes.traverse(hash => peer.markBlock(hash.hash, hash.number))
      } yield Nil

    case PeerRequest(peer, req @ Request(_, "NewBlock", _)) =>
      for {
        NewBlock(block) <- req.bodyAs[NewBlock]
        _               <- peer.markBlock(block.header.hash, block.header.number)
        resp <- syncStatus.get.flatMap[List[(PeerSelectStrategy[F], Message[F])]] {
          case SyncStatus.SyncDone =>
            for {
              blocks <- executor.handleReceivedBlock(ReceivedBlock(block, peer))
              resp   <- blocks.traverse(broadcastBlock).map(_.flatten)
            } yield resp

          case _ =>
            F.delay(log.debug(s"still in syncing, ignore ${block.tag} atm")).as(Nil)
        }
      } yield resp

    case PeerRequest(peer, req @ Request(id, "SignedTransactions", _)) =>
      for {
        stxs <- req.bodyAs[SignedTransactions]
        _ = log.debug(s"received ${stxs.txs.length} stxs from ${peer.id}")
        _    <- peer.markTxs(stxs)
        _    <- executor.txPool.addTransactions(stxs)
        resp <- Response.ok[F, SignedTransactions](id, stxs)
      } yield PeerSelectStrategy.withoutTxs(stxs) -> resp :: Nil
  }

  val service: PeerService[F] = {
    /*_*/
    (requestService <+> messageService).orNil
    /*_*/
  }

  val stream: Stream[F, Unit] =
    Stream(serve, sync.drain).parJoinUnbounded

  def broadcastBlock(block: Block): F[List[(PeerSelectStrategy[F], Message[F])]] =
    for {
      newBlockHash <- Request[F, NewBlockHashes](
        "NewBlockHashes",
        NewBlockHashes(BlockHash(block.header.hash, block.header.number) :: Nil))
      newBlock <- Request[F, NewBlock]("NewBlock", NewBlock(block))
    } yield
      List(
        PeerSelectStrategy.withoutBlock(block) -> newBlockHash,
        PeerSelectStrategy
          .withoutBlock(block)
          .andThen(PeerSelectStrategy.randomSelectSqrt(config.minBroadcastPeers)) -> newBlock
      )

  def sendMessages(messages: List[(PeerSelectStrategy[F], Message[F])]): F[Unit] =
    messages.traverse { case (strategy, message) => peerManager.distribute(strategy, message) }.void
}

object SyncManager {
  def apply[F[_]](
      config: SyncConfig,
      executor: BlockExecutor[F],
      status: SyncStatus = SyncStatus.Booting
  )(implicit F: ConcurrentEffect[F], T: Timer[F], M: Metrics[F]): F[SyncManager[F]] =
    for {
      syncStatus <- Ref.of[F, SyncStatus](status)
      fastSync   <- FastSync[F](config, executor.peerManager)
      fullSync = FullSync[F](config, executor, syncStatus)
    } yield SyncManager[F](config, executor, syncStatus, fullSync, fastSync)
}
