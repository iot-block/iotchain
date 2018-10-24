package jbok.core.peer

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import jbok.core.messages.{Message, Status}
import jbok.network.Connection
import scodec.bits.ByteVector

case class HandshakedPeer[F[_]](
    conn: Connection[F, Message],
    status: Ref[F, Status],
    knownBlocks: Ref[F, Set[ByteVector]],
    knownTxs: Ref[F, Set[ByteVector]]
)(implicit F: Sync[F]) {
  val id: String = conn.remoteAddress.toString

  def hasBlock(blockHash: ByteVector): F[Boolean] =
    knownBlocks.get.map(_.contains(blockHash))

  def knownBlock(blockHash: ByteVector): F[Unit] =
    knownBlocks.update(_ + blockHash)

  def hasTx(txHash: ByteVector): F[Boolean] =
    knownTxs.get.map(_.contains(txHash))

  def knownTx(txHash: ByteVector): F[Unit] =
    knownTxs.update(_ + txHash)
}

object HandshakedPeer {
  def apply[F[_]: Sync](conn: Connection[F, Message], status: Status): F[HandshakedPeer[F]] =
    for {
      status      <- Ref.of[F, Status](status)
      knownBlocks <- Ref.of[F, Set[ByteVector]](Set.empty)
      knownTxs    <- Ref.of[F, Set[ByteVector]](Set.empty)
    } yield HandshakedPeer[F](conn, status, knownBlocks, knownTxs)
}
