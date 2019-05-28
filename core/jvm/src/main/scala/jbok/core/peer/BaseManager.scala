package jbok.core.peer

import java.net.InetSocketAddress

import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.io.tcp.Socket
import jbok.codec.rlp.implicits._
import jbok.core.config.FullConfig
import jbok.core.ledger.History
import jbok.core.messages.Status
import jbok.core.queue.Queue
import jbok.network.tcp.implicits._
import jbok.network.{Message, Request}

import scala.util.control.NoStackTrace

final case class Incompatible(local: Status, remote: Status) extends NoStackTrace {
  override def toString: String = s"peer incompatible chainId:${local.chainId}/${remote.chainId} genesis:${local.genesisHash.toHex}/${remote.genesisHash.toHex}"
}

abstract class BaseManager[F[_]](config: FullConfig, history: History[F])(implicit F: Concurrent[F]) {
  def inbound: Queue[F, Peer[F], Message[F]]

  val connected: Ref[F, Map[PeerUri, (Peer[F], Socket[F])]] = Ref.unsafe(Map.empty)

  def close(uri: PeerUri): F[Unit] =
    connected.get.map(_.get(uri)).flatMap {
      case Some((_, socket)) => socket.endOfOutput >> socket.close
      case _                 => F.unit
    }

  val localStatus: F[Status] =
    for {
      genesis <- history.genesisHeader
      number  <- history.getBestBlockNumber
      td      <- history.getTotalDifficultyByNumber(number).map(_.getOrElse(BigInt(0)))
    } yield Status(history.chainId, genesis.hash, number, td, config.service.uri)

  def handshake(socket: Socket[F]): F[Peer[F]] =
    for {
      localStatus <- localStatus
      request = Request.binary[F, Status](Status.name, localStatus.asBytes)
      _            <- socket.writeMessage(request)
      remoteStatus <- socket.readMessage.flatMap(_.as[Status])
      remote       <- socket.remoteAddress.map(_.asInstanceOf[InetSocketAddress])
      peer <- if (!localStatus.isCompatible(remoteStatus)) {
        F.raiseError(Incompatible(localStatus, remoteStatus))
      } else {
        Peer[F](PeerUri.fromTcpAddr(remote), remoteStatus)
      }
    } yield peer
}
