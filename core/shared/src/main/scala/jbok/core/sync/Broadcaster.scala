package jbok.core.sync

import cats.effect.Effect
import cats.implicits._
import jbok.core.messages.{BlockHash, NewBlock, NewBlockHashes}
import jbok.core.peer.{HandshakedPeer, PeerId, PeerInfo, PeerManager}
import scodec.bits.ByteVector

import scala.concurrent.ExecutionContext
import scala.util.Random

case class Broadcaster[F[_]](peerManager: PeerManager[F])(implicit F: Effect[F], EC: ExecutionContext) {
  private[this] val log = org.log4s.getLogger

  def broadcastBlock(newBlock: NewBlock, peerHasBlocks: F[Map[PeerId, Set[ByteVector]]]): F[Unit] =
    for {
      peers       <- peerManager.handshakedPeers
      peersBlocks <- peerHasBlocks
      _ <- if (peers.isEmpty) {
        log.info(s"no handshaked peers, ignore broadcasting")
        F.unit
      } else {
        log.info(s"peersBlocks: ${peersBlocks}")
        broadcastBlock(newBlock, peers, peersBlocks)
      }
    } yield ()

  private def broadcastBlock(newBlock: NewBlock,
                             peers: Map[PeerId, HandshakedPeer],
                             peerHasBlocks: Map[PeerId, Set[ByteVector]]): F[Unit] = {
    val peersWithoutBlock = peers.collect {
      case (peerId, peer)
          if shouldSendNewBlock(newBlock, peer.peerInfo) && !alreadyHasBlock(newBlock, peerId, peerHasBlocks) =>
        peerId
    }.toSet

    if (peersWithoutBlock.isEmpty) {
      log.info(s"no worst peers, ignore broadcasting")
      F.unit
    } else {
      log.info(s"broadcast ${newBlock.block.tag} to ${peersWithoutBlock.size} peers")
      broadcastNewBlock(newBlock, peersWithoutBlock) *> broadcastNewBlockHash(newBlock, peersWithoutBlock)
    }
  }

  private def alreadyHasBlock(newBlock: NewBlock,
                              peerId: PeerId,
                              peerHasBlocks: Map[PeerId, Set[ByteVector]]): Boolean =
    peerHasBlocks.contains(peerId) && peerHasBlocks(peerId).contains(newBlock.block.header.hash)

  private def shouldSendNewBlock(newBlock: NewBlock, peerInfo: PeerInfo): Boolean =
    newBlock.block.header.number > peerInfo.status.bestNumber

  private def broadcastNewBlock(newBlock: NewBlock, peers: Set[PeerId], random: Boolean = false): F[Unit] = {
    val selected: Set[PeerId] = if (random) randomSelect(peers) else peers
    log.info(s"selected ${selected.size} peers to broadcast block")
    selected.toList
      .map { peerId =>
        peerManager.sendMessage(peerId, newBlock)
      }
      .sequence
      .void
  }

  private def broadcastNewBlockHash(newBlock: NewBlock, peers: Set[PeerId]): F[Unit] =
    peers.toList
      .map { peerId =>
        val newBlockHeader = newBlock.block.header
        val newBlockHashes = NewBlockHashes(BlockHash(newBlockHeader.hash, newBlockHeader.number) :: Nil)
        peerManager.sendMessage(peerId, newBlockHashes)
      }
      .sequence
      .void

  private def randomSelect(peers: Set[PeerId]): Set[PeerId] = {
    val numberOfPeersToSend = Math.sqrt(peers.size).toInt
    Random.shuffle(peers).take(numberOfPeersToSend)
  }
}
