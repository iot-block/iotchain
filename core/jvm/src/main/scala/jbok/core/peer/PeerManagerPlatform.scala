package jbok.core.peer
import java.net.InetSocketAddress
import java.nio.channels.{AsynchronousChannelGroup, AsynchronousCloseException, ClosedChannelException}

import cats.data.OptionT
import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, Timer}
import fs2._
import fs2.concurrent.{SignallingRef, Topic}
import jbok.common.concurrent.PriorityQueue
import jbok.core.History
import jbok.core.config.Configs.{PeerManagerConfig, SyncConfig}
import jbok.core.messages.{Message, Status}
import jbok.core.sync.SyncService
import jbok.crypto.signature.KeyPair
import jbok.network.Connection
import jbok.network.common.TcpUtil
import jbok.network.rlpx.handshake.AuthHandshaker
import cats.implicits._
import cats.effect.implicits._

import scala.concurrent.duration.FiniteDuration

object PeerManagerPlatform {
  def apply[F[_]](
      config: PeerManagerConfig,
      keyPair: KeyPair,
      sync: SyncConfig,
      history: History[F],
      maxQueueSize: Int = 64
  )(
      implicit F: ConcurrentEffect[F],
      T: Timer[F],
      AG: AsynchronousChannelGroup
  ): F[PeerManager[F]] =
    for {
      incoming  <- Ref.of[F, Map[InetSocketAddress, Peer[F]]](Map.empty)
      outgoing  <- Ref.of[F, Map[InetSocketAddress, Peer[F]]](Map.empty)
      nodeQueue <- PriorityQueue.bounded[F, PeerNode](maxQueueSize)
      messages  <- Topic[F, Option[(Peer[F], Message)]](None)
      pipe = SyncService[F](sync, history).pipe
      haltWhenTrue <- SignallingRef[F, Boolean](true)
    } yield
      new PeerManager[F](config, keyPair, history, incoming, outgoing, nodeQueue, messages, pipe, haltWhenTrue) {
        private[jbok] def handshakeIncoming(conn: Connection[F]): F[Peer[F]] =
          for {
            handshaker   <- AuthHandshaker[F](keyPair)
            result       <- handshaker.accept(conn)
            localStatus  <- localStatus
            _            <- conn.write[Message](localStatus, Some(config.handshakeTimeout))
            remoteStatus <- conn.read[Message](Some(config.handshakeTimeout)).map(_.asInstanceOf[Status])
            _ <- if (!localStatus.isCompatible(remoteStatus)) {
              F.raiseError(new Exception("incompatible peer"))
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
            _            <- conn.write[Message](localStatus, Some(config.handshakeTimeout))
            remoteStatus <- conn.read[Message](Some(config.handshakeTimeout)).map(_.asInstanceOf[Status])
            _ <- if (!localStatus.isCompatible(remoteStatus)) {
              F.raiseError(new Exception("incompatible peer"))
            } else {
              F.unit
            }
            peer <- Peer[F](KeyPair.Public(result.remotePubKey), conn, remoteStatus)
          } yield peer
      }
}
