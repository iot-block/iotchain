package jbok.core.peer

import cats.effect.concurrent.Ref
import cats.effect.{Concurrent, Sync}
import cats.implicits._
import fs2.concurrent.Queue
import jbok.core.messages.{SignedTransactions, Status}
import jbok.network.Message
import scodec.bits.ByteVector
import jbok.codec.rlp.implicits._
import jbok.common.math.N
import jbok.crypto._

final case class Peer[F[_]](
    uri: PeerUri,
    queue: Queue[F, Message[F]],
    status: Ref[F, Status],
    knownBlocks: Ref[F, Set[ByteVector]],
    knownTxs: Ref[F, Set[ByteVector]]
)(implicit F: Sync[F]) {
  import Peer._

  def hasBlock(blockHash: ByteVector): F[Boolean] =
    knownBlocks.get.map(_.contains(blockHash))

  def hasTxs(stxs: SignedTransactions): F[Boolean] =
    knownTxs.get.map(_.contains(stxs.encoded.bytes.kec256))

  def markBlock(blockHash: ByteVector, number: N): F[Unit] =
    knownBlocks.update(s => s.take(MaxKnownBlocks - 1) + blockHash) >>
      status.update(s => s.copy(bestNumber = s.bestNumber.max(number)))

  def markTxs(stxs: SignedTransactions): F[Unit] =
    knownTxs.update(known => known.take(MaxKnownTxs - 1) + stxs.encoded.bytes.kec256)

  def markStatus(newStatus: Status): F[Unit] =
    status.update(s => if (newStatus.td > s.td) s.copy(bestNumber = newStatus.bestNumber, td = newStatus.td) else s)
}

object Peer {
  val MaxKnownTxs    = 32768
  val MaxKnownBlocks = 1024

  def apply[F[_]: Concurrent](uri: PeerUri, status: Status): F[Peer[F]] =
    for {
      queue       <- Queue.bounded[F, Message[F]](10)
      status      <- Ref.of[F, Status](status)
      knownBlocks <- Ref.of[F, Set[ByteVector]](Set.empty)
      knownTxs    <- Ref.of[F, Set[ByteVector]](Set.empty)
    } yield Peer[F](uri, queue, status, knownBlocks, knownTxs)
}
