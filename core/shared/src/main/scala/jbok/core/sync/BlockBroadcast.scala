package jbok.core.sync

import cats.effect.Sync
import jbok.core.messages.{BlockHash, NewBlock, NewBlockHashes}
import jbok.p2p.{Peer, PeerInfo, PeerManager}

import scala.util.Random
import cats.implicits._

class BlockBroadcast[F[_]](peerManager: PeerManager[F])(implicit F: Sync[F]) {
  def broadcastBlock(newBlock: NewBlock, peers: Map[Peer, PeerInfo]): F[Unit] = {
    val peersWithoutBlock = peers.collect {
      case (peer, peerInfo) if shouldSendNewBlock(newBlock, peerInfo) => peer
    }.toSet

    broadcastNewBlock(newBlock, peersWithoutBlock) *>
      broadcastNewBlockHash(newBlock, peersWithoutBlock)
  }

  private def shouldSendNewBlock(newBlock: NewBlock, peerInfo: PeerInfo): Boolean =
    newBlock.block.header.number > peerInfo.maxBlockNumber

  private def broadcastNewBlock(newBlock: NewBlock, peers: Set[Peer]): F[Unit] =
    obtainRandomPeerSubset(peers).toList
      .map { peer =>
        peerManager.sendMessage(newBlock.asBytes, peer.id)
      }
      .sequence
      .void

  private def broadcastNewBlockHash(newBlock: NewBlock, peers: Set[Peer]): F[Unit] = {
    peers.toList
      .map { peer =>
        val newBlockHeader = newBlock.block.header
        val newBlockHashMsg = NewBlockHashes(Seq(BlockHash(newBlockHeader.hash, newBlockHeader.number)))
        peerManager.sendMessage(newBlockHashMsg.asBytes, peer.id)
      }
      .sequence
      .void
  }

  /**
    * Obtains a random subset of peers. The returned set will verify:
    *   subsetPeers.size == sqrt(peers.size)
    *
    * @param peers
    * @return a random subset of peers
    */
  private def obtainRandomPeerSubset(peers: Set[Peer]): Set[Peer] = {
    val numberOfPeersToSend = Math.sqrt(peers.size).toInt
    Random.shuffle(peers).take(numberOfPeersToSend)
  }
}
