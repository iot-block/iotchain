package jbok.core.peer

import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2._
import jbok.codec.rlp.implicits._
import jbok.common.log.Logger
import jbok.core.NodeStatus
import jbok.core.messages.{BlockHash, NewBlock, NewBlockHashes, SignedTransactions, Status}
import jbok.core.models.Block
import jbok.core.peer.PeerSelector.PeerSelector
import jbok.core.queue.{Consumer, Producer}
import jbok.network.Request

class PeerMessageHandler[F[_]](
    txInbound: Producer[F, Peer[F], SignedTransactions],
    txOutbound: Consumer[F, PeerSelector[F], SignedTransactions],
    blockInbound: Producer[F, Peer[F], Block],
    blockOutbound: Consumer[F, PeerSelector[F], Block],
    statusInbound: Producer[F, Peer[F], Status],
    statusOutbound: Consumer[F, PeerSelector[F], Status],
    peerManager: PeerManager[F],
    status: Ref[F, NodeStatus]
)(implicit F: Concurrent[F]) {
  private[this] val log = Logger[F]

  def onNewBlockHashes(peer: Peer[F], hashes: List[BlockHash]): F[Unit] =
    hashes.traverse_(hash => peer.markBlock(hash.hash, hash.number))

  def onNewBlock(peer: Peer[F], block: Block): F[Unit] =
    status.get.flatMap {
      case NodeStatus.Done => blockInbound.produce(peer, block)
      case _               => F.unit
    }

  def onSignedTransactions(peer: Peer[F], stxs: SignedTransactions): F[Unit] =
    txInbound.produce(peer, stxs)

  def onStatus(peer: Peer[F], remoteStatus: Status):F[Unit] =
//    statusInbound.produce(peer, remoteStatus)
    for {
      localStatus <- peerManager.outgoing.localStatus
      _ <- if (!localStatus.isCompatible(remoteStatus)) {
        F.raiseError(Incompatible(localStatus, remoteStatus))
      }else{
        peer.markStatus(remoteStatus)
      }
    } yield ()

  val consume: Stream[F, Unit] =
    peerManager.inbound.evalMap {
      case (peer, req @ Request(_, NewBlockHashes.name, _, _)) =>
        for {
          hashes <- req.as[NewBlockHashes].map(_.hashes)
          _      <- onNewBlockHashes(peer, hashes)
        } yield ()

      case (peer, req @ Request(_, NewBlock.name, _, _)) =>
        for {
          block <- req.as[NewBlock].map(_.block)
          _     <- onNewBlock(peer, block)
        } yield ()

      case (peer, req @ Request(_, SignedTransactions.name, _, _)) =>
        for {
          stxs <- req.as[SignedTransactions]
          _    <- onSignedTransactions(peer, stxs)
        } yield ()

      case (peer, req @ Request(_, Status.name, _, _)) =>
        for {
          status <- req.as[Status]
          _ <- onStatus(peer, status)
        } yield ()

      case _ => F.unit
    }

  val produce: Stream[F, Unit] = {
    Stream(
      blockOutbound.consume.map { case (selector, block) => selector -> Request.binary[F, NewBlock](NewBlock.name, NewBlock(block).encoded) },
      txOutbound.consume.map { case (selector, tx)       => selector -> Request.binary[F, SignedTransactions](SignedTransactions.name, tx.encoded) },
      statusOutbound.consume.map { case (selector, st)       => selector -> Request.binary[F, Status](Status.name, st.encoded) }
    ).parJoinUnbounded
      .through(peerManager.outbound)
  }

  val stream: Stream[F, Unit] =
    Stream.eval_(log.i(s"starting Core/PeerMessageHandler")) ++
      consume merge produce
}
