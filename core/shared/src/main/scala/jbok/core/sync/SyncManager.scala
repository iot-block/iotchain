package jbok.core.sync

import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import fs2._
import fs2.concurrent.SignallingRef
import jbok.core.config.Configs.SyncConfig
import jbok.core.ledger.BlockExecutor
import jbok.core.ledger.TypedBlock.ReceivedBlock
import jbok.core.messages._
import jbok.core.models.Block
import jbok.core.peer.PeerSelectStrategy.PeerSelectStrategy
import jbok.core.peer._
import scodec.bits.ByteVector

final case class SyncManager[F[_]](
    config: SyncConfig,
    executor: BlockExecutor[F],
    fullSync: FullSync[F],
    fastSync: FastSync[F],
    haltWhenTrue: SignallingRef[F, Boolean]
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  private[this] val log = org.log4s.getLogger("SyncManager")

  val peerManager = executor.peerManager

  def serve: Stream[F, Unit] =
    peerManager.messageQueue.dequeue.evalMap { req =>
      log.trace(s"received request ${req.message}")
      service.run(req).flatMap { response: List[(PeerSelectStrategy[F], Message)] =>
        response
          .traverse[F, Unit] {
            case (strategy, message) =>
              peerManager.distribute(strategy, message)
          }
          .void
      }
    }

  val history = executor.history

  val requestService: PeerRoutes[F] = PeerRoutes.of[F] {
    case Request(peer, GetReceipts(hashes, id)) =>
      for {
        receipts <- hashes.traverse(history.getReceiptsByHash).map(_.flatten)
      } yield PeerSelectStrategy.one(peer) -> Receipts(receipts, id) :: Nil

    case Request(peer, GetBlockBodies(hashes, id)) =>
      for {
        bodies <- hashes.traverse(hash => history.getBlockBodyByHash(hash)).map(_.flatten)
      } yield PeerSelectStrategy.one(peer) -> BlockBodies(bodies, id) :: Nil

    case Request(peer, GetBlockHeaders(block, maxHeaders, skip, reverse, id)) =>
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
            .map(values => PeerSelectStrategy.one(peer) -> BlockHeaders(values, id) :: Nil)

        case _ =>
          F.pure(Nil)
      }

    case Request(peer, GetNodeData(nodeHashes, id)) =>
      val nodeData = nodeHashes
        .traverse[F, Option[ByteVector]] {
          case NodeHash.StateMptNodeHash(v)           => history.getMptNode(v)
          case NodeHash.StorageRootHash(v)            => history.getMptNode(v)
          case NodeHash.ContractStorageMptNodeHash(v) => history.getMptNode(v)
          case NodeHash.EvmCodeHash(v)                => history.getCode(v)
        }
        .map(_.flatten)

      nodeData.map(values => PeerSelectStrategy.one(peer) -> NodeData(values, id) :: Nil)
  }

  val messageService: PeerRoutes[F] = PeerRoutes.of[F] {
    case Request(peer, NewBlockHashes(hashes)) =>
      hashes.traverse(hash => peer.markBlock(hash.hash)) *> F.pure(Nil)

    case Request(peer, NewBlock(block)) =>
      executor
        .handleReceivedBlock(ReceivedBlock(block, peer))
        .map(blocks => blocks.flatMap(broadcastBlock))

    case Request(peer, stxs: SignedTransactions) =>
      log.debug(s"received ${stxs.txs.length} stxs from ${peer.id}")
      for {
        _        <- peer.markTxs(stxs.txs.map(_.hash))
        response <- executor.txPool.handleReceived(peer, stxs)
      } yield response
  }

  val service: PeerService[F] = {
    /*_*/
    (requestService <+> messageService).orNil
    /*_*/
  }

  val stream: Stream[F, Unit] =
    Stream
      .eval(haltWhenTrue.set(false))
      .flatMap(_ => serve)
      .interruptWhen(haltWhenTrue)
      .onFinalize(haltWhenTrue.set(true))

  def broadcastBlock(block: Block): List[(PeerSelectStrategy[F], Message)] =
    List(
      PeerSelectStrategy.withoutBlock(block) -> NewBlockHashes(
        BlockHash(block.header.hash, block.header.number) :: Nil),
      PeerSelectStrategy
        .withoutBlock(block)
        .andThen(PeerSelectStrategy.randomSelectSqrt(config.minBroadcastPeers)) -> NewBlock(block)
    )

  def sendMessages(messages: List[(PeerSelectStrategy[F], Message)]): F[Unit] =
    messages.traverse { case (strategy, message) => peerManager.distribute(strategy, message) }.void
}

object SyncManager {
  def apply[F[_]](
      config: SyncConfig,
      executor: BlockExecutor[F],
  )(implicit F: ConcurrentEffect[F], T: Timer[F]): F[SyncManager[F]] =
    for {
      fastSync <- FastSync[F](config, executor.peerManager)
      fullSync = FullSync[F](config, executor)
      haltWhenTrue <- SignallingRef[F, Boolean](true)
    } yield SyncManager[F](config, executor, fullSync, fastSync, haltWhenTrue)
}
