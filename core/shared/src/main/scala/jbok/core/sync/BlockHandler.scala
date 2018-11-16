package jbok.core.sync

import cats.effect.ConcurrentEffect
import cats.implicits._
import jbok.core.config.Configs.SyncConfig
import jbok.core.ledger.BlockExecutor
import jbok.core.ledger.BlockImportResult._
import jbok.core.messages.{BlockHash, Message, NewBlock, NewBlockHashes}
import jbok.core.models.Block
import jbok.core.peer.{Peer, _}
import jbok.core.pool.{OmmerPool, TxPool}

import scala.util.Random

final case class BlockHandler[F[_]](
    config: SyncConfig,
    executor: BlockExecutor[F],
    txPool: TxPool[F],
    ommerPool: OmmerPool[F]
)(implicit F: ConcurrentEffect[F]) {
  private[this] val log = org.log4s.getLogger("BlockHandler")

  val service = PeerRoutes.of[F] {
    case Request(peer, peerSet, NewBlockHashes(hashes)) =>
      hashes.traverse(hash => peer.markBlock(hash.hash)) *> F.pure(Nil)

    case Request(peer, peerSet, NewBlock(block)) =>
      peer.markBlock(block.header.hash) *> handleBlock(block, peerSet)
  }

  def handleBlock(block: Block, peerSet: PeerSet[F]): F[List[(Peer[F], Message)]] =
    executor.importBlock(block).flatMap {
      case Succeed(newBlocks, _) =>
        updateTxAndOmmerPools(peerSet, newBlocks, Nil) *> newBlocks
          .traverse(block => broadcastBlock(peerSet, block))
          .map(_.flatten)

      case Pooled =>
        F.pure(Nil)

      case Failed(error) =>
        F.pure(Nil)
    }

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

  private def updateTxAndOmmerPools(peerSet: PeerSet[F],
                                    blocksAdded: List[Block],
                                    blocksRemoved: List[Block]): F[Unit] = {
    log.debug(s"update txPool and ommerPool with ${blocksAdded.length} ADDs and ${blocksRemoved.length} REMOVEs")
    for {
      _ <- ommerPool.addOmmers(blocksRemoved.headOption.toList.map(_.header))
      _ <- blocksRemoved.map(_.body.transactionList).traverse(txs => txPool.addTransactions(txs, peerSet))
      _ <- blocksAdded.map { block =>
        ommerPool.removeOmmers(block.header :: block.body.uncleNodesList) *>
          txPool.removeTransactions(block.body.transactionList)
      }.sequence
    } yield ()
  }
}
