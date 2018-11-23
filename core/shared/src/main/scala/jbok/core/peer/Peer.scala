package jbok.core.peer

import cats.effect.concurrent.{Deferred, Ref}
import cats.effect.{Concurrent, ConcurrentEffect, Sync}
import cats.implicits._
import fs2._
import fs2.concurrent.{Queue, SignallingRef}
import jbok.core.messages.{Message, Status}
import jbok.crypto._
import jbok.crypto.signature.KeyPair
import jbok.network.Connection
import scodec.bits.ByteVector

case class Peer[F[_]](
    pk: KeyPair.Public,
    conn: Connection[F, Message],
    status: Ref[F, Status],
    knownBlocks: Ref[F, Set[ByteVector]],
    knownTxs: Ref[F, Set[ByteVector]]
)(implicit F: Sync[F]) {
  import Peer._

  val id: String = s"Peer(${pk.bytes.kec256.toHex.take(7)})"

  def hasBlock(blockHash: ByteVector): F[Boolean] =
    knownBlocks.get.map(_.contains(blockHash))

  def hasTx(txHash: ByteVector): F[Boolean] =
    knownTxs.get.map(_.contains(txHash))

  def markBlock(blockHash: ByteVector): F[Unit] =
    knownBlocks.update(s => if (s.size >= MaxKnownBlocks) s.take(MaxKnownBlocks - 1) + blockHash else s + blockHash)

  def markTxs(txHashes: List[ByteVector]): F[Unit] =
    knownTxs.update(known => known.take(MaxKnownTxs - txHashes.length) ++ txHashes)
}

object Peer {
  val MaxKnownTxs    = 32768
  val MaxKnownBlocks = 1024

  def apply[F[_]: Sync](pk: KeyPair.Public, conn: Connection[F, Message], status: Status): F[Peer[F]] =
    for {
      status      <- Ref.of[F, Status](status)
      knownBlocks <- Ref.of[F, Set[ByteVector]](Set.empty)
      knownTxs    <- Ref.of[F, Set[ByteVector]](Set.empty)
    } yield Peer[F](pk, conn, status, knownBlocks, knownTxs)

  def dummy[F[_]: ConcurrentEffect](pk: KeyPair.Public, status: Status): F[Peer[F]] =
    for {
      in           <- Queue.bounded[F, Message](1)
      out          <- Queue.bounded[F, Message](1)
      promises     <- Ref.of[F, Map[String, Deferred[F, Message]]](Map.empty)
      haltWhenTrue <- SignallingRef[F, Boolean](true)
      stream = Stream.empty.covaryAll[F, Unit]
      conn   = Connection[F, Message](stream, in, out, promises, true, haltWhenTrue)
      peer <- Peer[F](pk, conn, status)
    } yield peer
}
