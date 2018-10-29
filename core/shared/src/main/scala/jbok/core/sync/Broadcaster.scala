package jbok.core.sync

import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import jbok.common._
import jbok.core.messages.{BlockHash, Message, NewBlock, NewBlockHashes}
import jbok.core.peer.{Peer, PeerManager}

import scala.util.Random

case class Broadcaster[F[_]](peerManager: PeerManager[F])(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  private[this] val log = org.log4s.getLogger

  def broadcastBlock(newBlock: NewBlock): F[Unit] =
    for {
      peers <- peerManager.connected
      _ <- if (peers.isEmpty) {
        log.info(s"no peers, ignore broadcasting")
        F.unit
      } else {
        for {
          peersWithoutBlock <- peers
            .traverse { peer =>
              (higherThanPeer(newBlock, peer) && !peer.hasBlock(newBlock.block.header.hash)).map {
                case true  => Some(peer)
                case false => None
              }
            }
            .map(_.flatten)
          _ <- broadcastNewBlock(newBlock, peersWithoutBlock)
          _ <- broadcastNewBlockHash(newBlock, peersWithoutBlock)
        } yield ()
      }
    } yield ()

  private def higherThanPeer(newBlock: NewBlock, peer: Peer[F]): F[Boolean] =
    peer.status.get.map(_.bestNumber < newBlock.block.header.number)

  private def broadcastNewBlock(newBlock: NewBlock, peers: List[Peer[F]], random: Boolean = false): F[Unit] = {
    val selected: List[Peer[F]] = if (random) randomSelect(peers) else peers
    log.info(s"selected ${selected.size} peers to broadcast block")
    selected.traverse(_.conn.write[Message](newBlock)).void
  }

  private def broadcastNewBlockHash(newBlock: NewBlock, peers: List[Peer[F]]): F[Unit] =
    peers.traverse { peer =>
      val newBlockHeader = newBlock.block.header
      val newBlockHashes = NewBlockHashes(BlockHash(newBlockHeader.hash, newBlockHeader.number) :: Nil)
      peer.conn.write[Message](newBlockHashes)
    }.void

  private def randomSelect(peers: List[Peer[F]]): List[Peer[F]] = {
    val numberOfPeersToSend = Math.sqrt(peers.size).toInt
    Random.shuffle(peers).take(numberOfPeersToSend)
  }
}
