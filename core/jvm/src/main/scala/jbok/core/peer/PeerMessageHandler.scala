package jbok.core.peer

import cats.effect.Concurrent
import cats.implicits._
import fs2._
import jbok.codec.rlp.implicits._
import jbok.core.messages.{BlockHash, NewBlock, NewBlockHashes, SignedTransactions}
import jbok.core.models.Block
import jbok.core.peer.PeerSelector.PeerSelector
import jbok.core.queue.{Consumer, Producer}
import jbok.network.Request

class PeerMessageHandler[F[_]](
    txInbound: Producer[F, Peer[F], SignedTransactions],
    txOutbound: Consumer[F, PeerSelector[F], SignedTransactions],
    blockInbound: Producer[F, Peer[F], Block],
    blockOutbound: Consumer[F, PeerSelector[F], Block],
    peerManager: PeerManager[F]
)(implicit F: Concurrent[F]) {
  def onNewBlockHashes(peer: Peer[F], hashes: List[BlockHash]): F[Unit] =
    hashes.traverse_(hash => peer.markBlock(hash.hash, hash.number))

  def onNewBlock(peer: Peer[F], block: Block): F[Unit] =
    blockInbound.produce(peer, block)

  def onSignedTransactions(peer: Peer[F], stxs: SignedTransactions): F[Unit] =
    txInbound.produce(peer, stxs)

  val consume: Stream[F, Unit] =
    peerManager.inbound.evalMap {
      case (peer, req @ Request(id, NewBlockHashes.name, _, _)) =>
        for {
          hashes <- req.as[NewBlockHashes].map(_.hashes)
          _      <- onNewBlockHashes(peer, hashes)
        } yield ()

      case (peer, req @ Request(id, NewBlock.name, _, _)) =>
        for {
          block <- req.as[NewBlock].map(_.block)
          _     <- onNewBlock(peer, block)
        } yield ()

      case (peer, req @ Request(id, SignedTransactions.name, _, _)) =>
        for {
          stxs <- req.as[SignedTransactions]
          _    <- onSignedTransactions(peer, stxs)
        } yield ()

      case _ => F.unit
    }

  val produce: Stream[F, Unit] = {
    Stream(
      blockOutbound.consume.map { case (selector, block) => selector -> Request.binary[F, NewBlock](NewBlock.name, NewBlock(block).asValidBytes) },
      txOutbound.consume.map { case (selector, tx)       => selector -> Request.binary[F, SignedTransactions](SignedTransactions.name, tx.asValidBytes) }
    ).parJoinUnbounded
      .through(peerManager.outbound)
  }

  val stream: Stream[F, Unit] =
    consume merge produce
}
