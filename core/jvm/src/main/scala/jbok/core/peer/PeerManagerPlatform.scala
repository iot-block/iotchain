package jbok.core.peer
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import better.files._
import cats.data.OptionT
import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, IO, Timer}
import cats.implicits._
import fs2.concurrent.{SignallingRef, Topic}
import jbok.common.concurrent.PriorityQueue
import jbok.core.History
import jbok.core.config.Configs.{PeerManagerConfig, SyncConfig}
import jbok.core.messages.{Message, Status}
import jbok.core.sync.SyncService
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import jbok.network.Connection
import jbok.network.rlpx.handshake.AuthHandshaker

object PeerManagerPlatform {
  private[this] val log = org.log4s.getLogger

  def loadNodeKey(path: String): IO[KeyPair] =
    for {
      secret <- IO(File(path).lines(DefaultCharset).head).map(str => KeyPair.Secret(str))
      pubkey <- Signature[ECDSA].generatePublicKey(secret)
    } yield KeyPair(pubkey, secret)

  def saveNodeKey(path: String, keyPair: KeyPair): IO[Unit] =
    IO(File(path).overwrite(keyPair.secret.bytes.toHex))

  def loadOrGenerateNodeKey(path: String): IO[KeyPair] =
    loadNodeKey(path).attempt.flatMap {
      case Left(e) =>
        log.error(e)(s"read nodekey at ${path} failed, generating a random one")
        for {
          keyPair <- Signature[ECDSA].generateKeyPair()
          _       <- saveNodeKey(path, keyPair)
        } yield keyPair

      case Right(nodeKey) =>
        IO.pure(nodeKey)
    }

  def apply[F[_]](
      config: PeerManagerConfig,
      keyPairOpt: Option[KeyPair],
      sync: SyncConfig,
      history: History[F],
      maxQueueSize: Int = 64
  )(
      implicit F: ConcurrentEffect[F],
      T: Timer[F],
      AG: AsynchronousChannelGroup
  ): F[PeerManager[F]] =
    for {
      keyPair   <- OptionT.fromOption[F](keyPairOpt).getOrElseF(F.liftIO(loadOrGenerateNodeKey(config.nodekeyPath)))
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
