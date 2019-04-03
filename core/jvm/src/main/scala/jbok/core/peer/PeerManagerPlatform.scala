package jbok.core.peer

import java.nio.channels.AsynchronousChannelGroup

import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.concurrent.Queue
import jbok.common.concurrent.PriorityQueue
import jbok.core.config.Configs.CoreConfig
import jbok.core.ledger.History
import jbok.core.messages.Status
import jbok.core.rlpx.handshake.AuthHandshaker
import jbok.crypto.signature.KeyPair
import jbok.network.{Connection, Request}
import jbok.codec.rlp.implicits._

object PeerManagerPlatform {
  def apply[F[_]](
      config: CoreConfig,
      history: History[F],
      maxQueueSize: Int = 64
  )(
      implicit F: ConcurrentEffect[F],
      CS: ContextShift[F],
      T: Timer[F],
      AG: AsynchronousChannelGroup
  ): F[PeerManager[F]] =
    for {
      incoming     <- Ref.of[F, Map[KeyPair.Public, Peer[F]]](Map.empty)
      outgoing     <- Ref.of[F, Map[KeyPair.Public, Peer[F]]](Map.empty)
      nodeQueue    <- PriorityQueue.bounded[F, PeerNode](maxQueueSize)
      messageQueue <- Queue.bounded[F, PeerRequest[F]](maxQueueSize)
    } yield
      new PeerManager[F](config.peer, config.nodeKeyPair, history, incoming, outgoing, nodeQueue, messageQueue) {
        private[jbok] def handshakeIncoming(conn: Connection[F]): F[Peer[F]] =
          for {
            handshaker   <- AuthHandshaker[F](keyPair)
            result       <- handshaker.accept(conn)
            localStatus  <- localStatus
            request      <- Request.binary[F, Status]("Status", localStatus)
            _            <- conn.write(request)
            remoteStatus <- conn.read.flatMap(_.binaryBodyAs[Status])
            _ <- if (!localStatus.isCompatible(remoteStatus)) {
              F.raiseError(PeerErr.Incompatible(localStatus, remoteStatus))
            } else {
              F.unit
            }
            peer <- Peer[F](KeyPair.Public(result.remotePubKey), conn, remoteStatus)
          } yield peer

        private[jbok] def handshakeOutgoing(conn: Connection[F], remotePk: KeyPair.Public): F[Peer[F]] =
          for {
            handshaker   <- AuthHandshaker[F](keyPair)
            result       <- handshaker.connect(conn, remotePk)
            localStatus  <- localStatus
            request      <- Request.binary[F, Status]("Status", localStatus)
            _            <- conn.write(request)
            remoteStatus <- conn.read.flatMap(_.binaryBodyAs[Status])
            _ <- if (!localStatus.isCompatible(remoteStatus)) {
              F.raiseError(PeerErr.Incompatible(localStatus, remoteStatus))
            } else {
              F.unit
            }
            peer <- Peer[F](KeyPair.Public(result.remotePubKey), conn, remoteStatus)
          } yield peer
      }
}
