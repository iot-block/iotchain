package jbok.core.sync

import cats.effect.Effect
import cats.implicits._
import jbok.core.messages.{BlockHash, NewBlock, NewBlockHashes}
import jbok.core.peer.{HandshakedPeer, PeerId, PeerInfo, PeerManager}

import scala.util.Random

class Broadcaster[F[_]](peerManager: PeerManager[F])(implicit F: Effect[F]) {
  private[this] val log = org.log4s.getLogger

  def broadcastBlock(newBlock: NewBlock): F[Unit] =
    for {
      peers <- peerManager.handshakedPeers
      _ <- if (peers.isEmpty) {
        log.info(s"no handshaked peers, ignore broadcasting")
        F.unit
      } else {
        broadcastBlock(newBlock, peers)
      }
    } yield ()

  def broadcastBlock(newBlock: NewBlock, peers: Map[PeerId, HandshakedPeer]): F[Unit] = {
    val peersWithoutBlock = peers.collect {
      case (peerId, peer) if shouldSendNewBlock(newBlock, peer.peerInfo) => peerId
    }.toSet

    if (peersWithoutBlock.isEmpty) {
      log.info(s"no worst peers, ignore broadcasting")
      F.unit
    } else {
      log.info(s"broadcast newBlock to ${peersWithoutBlock.size} peers")
      broadcastNewBlock(newBlock, peersWithoutBlock) *> broadcastNewBlockHash(newBlock, peersWithoutBlock)
    }
  }

  private def shouldSendNewBlock(newBlock: NewBlock, peerInfo: PeerInfo): Boolean =
    newBlock.block.header.number > peerInfo.maxBlockNumber

  private def broadcastNewBlock(newBlock: NewBlock, peers: Set[PeerId]): F[Unit] = {
    val selected = randomSelect(peers)
    log.info(s"random selected ${selected.size} peers to broadcast block")
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
