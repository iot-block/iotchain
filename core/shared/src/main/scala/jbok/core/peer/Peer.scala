package jbok.core.peer

import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, ContextShift, Sync}
import cats.implicits._
import jbok.core.messages.{SignedTransactions, Status}
import jbok.crypto._
import jbok.crypto.signature.KeyPair
import jbok.network.Connection
import scodec.bits.ByteVector

final case class Peer[F[_]](
    pk: KeyPair.Public,
    conn: Connection[F],
    status: Ref[F, Status],
    knownBlocks: Ref[F, Set[ByteVector]],
    knownTxs: Ref[F, Set[SignedTransactions]]
)(implicit F: Sync[F]) {
  import Peer._

  val id: String = s"Peer(${pk.bytes.kec256.toHex.take(7)})"

  def hasBlock(blockHash: ByteVector): F[Boolean] =
    knownBlocks.get.map(_.contains(blockHash))

  def hasTxs(stxs: SignedTransactions): F[Boolean] =
    knownTxs.get.map(_.contains(stxs))

  def markBlock(blockHash: ByteVector, number: BigInt): F[Unit] =
    knownBlocks.update(s => s.take(MaxKnownBlocks - 1) + blockHash) >>
      status.update(s => s.copy(bestNumber = s.bestNumber.max(number)))

  def markTxs(stxs: SignedTransactions): F[Unit] =
    knownTxs.update(known => known.take(MaxKnownTxs - 1) + stxs)

  override def toString: String =
    s"Peer(${id})"
}

object Peer {
  val MaxKnownTxs    = 32768
  val MaxKnownBlocks = 1024

  def apply[F[_]: Sync](pk: KeyPair.Public, conn: Connection[F], status: Status): F[Peer[F]] =
    for {
      status      <- Ref.of[F, Status](status)
      knownBlocks <- Ref.of[F, Set[ByteVector]](Set.empty)
      knownTxs    <- Ref.of[F, Set[SignedTransactions]](Set.empty)
    } yield Peer[F](pk, conn, status, knownBlocks, knownTxs)

  def dummy[F[_]: ConcurrentEffect](pk: KeyPair.Public, status: Status)(implicit CS: ContextShift[F]): F[Peer[F]] =
    Connection.dummy[F].flatMap(conn => Peer[F](pk, conn, status))
}
