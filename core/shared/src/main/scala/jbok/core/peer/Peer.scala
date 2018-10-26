package jbok.core.peer

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import jbok.core.messages.Status
import jbok.crypto._
import jbok.crypto.signature.KeyPair
import jbok.network.Connection
import scodec.bits.ByteVector

case class Peer[F[_]](
    pk: KeyPair.Public,
    conn: Connection[F],
    status: Ref[F, Status],
    knownBlocks: Ref[F, Set[ByteVector]],
    knownTxs: Ref[F, Set[ByteVector]]
)(implicit F: Sync[F]) {
  val id: String = pk.bytes.kec256.toHex

  def hasBlock(blockHash: ByteVector): F[Boolean] =
    knownBlocks.get.map(_.contains(blockHash))

  def knownBlock(blockHash: ByteVector): F[Unit] =
    knownBlocks.update(_ + blockHash)

  def hasTx(txHash: ByteVector): F[Boolean] =
    knownTxs.get.map(_.contains(txHash))

  def knownTx(txHash: ByteVector): F[Unit] =
    knownTxs.update(_ + txHash)
}

object Peer {
  def apply[F[_]: Sync](pk: KeyPair.Public, conn: Connection[F], status: Status): F[Peer[F]] =
    for {
      status      <- Ref.of[F, Status](status)
      knownBlocks <- Ref.of[F, Set[ByteVector]](Set.empty)
      knownTxs    <- Ref.of[F, Set[ByteVector]](Set.empty)
    } yield Peer[F](pk, conn, status, knownBlocks, knownTxs)
}
