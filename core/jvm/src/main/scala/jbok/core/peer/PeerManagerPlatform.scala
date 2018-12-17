package jbok.core.peer

import java.nio.channels.AsynchronousChannelGroup

import better.files._
import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.concurrent.Queue
import jbok.common.concurrent.PriorityQueue
import jbok.core.config.Configs.PeerConfig
import jbok.core.ledger.History
import jbok.core.messages.{Message, Status}
import jbok.core.rlpx.handshake.AuthHandshaker
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import jbok.network.Connection

object PeerManagerPlatform {
  private[this] val log = jbok.common.log.getLogger("PeerManager")

  def loadNodeKey[F[_]: Sync](path: String): F[KeyPair] =
    for {
      secret <- Sync[F].delay(File(path).lines(DefaultCharset).head).map(str => KeyPair.Secret(str))
      pubkey <- Signature[ECDSA].generatePublicKey[F](secret)
    } yield KeyPair(pubkey, secret)

  def saveNodeKey[F[_]: Sync](path: String, keyPair: KeyPair): F[Unit] =
    Sync[F].delay(File(path).overwrite(keyPair.secret.bytes.toHex))

  def loadOrGenerateNodeKey[F[_]: Sync](path: String): F[KeyPair] =
    loadNodeKey[F](path).attempt.flatMap {
      case Left(e) =>
        log.info(s"read nodekey at ${path} failed, generating a random one")
        for {
          keyPair <- Signature[ECDSA].generateKeyPair[F]()
          _       <- saveNodeKey[F](path, keyPair)
        } yield keyPair

      case Right(nodeKey) =>
        Sync[F].pure(nodeKey)
    }

  def apply[F[_]](
      config: PeerConfig,
      history: History[F],
      maxQueueSize: Int = 64
  )(
      implicit F: ConcurrentEffect[F],
      CS: ContextShift[F],
      T: Timer[F],
      AG: AsynchronousChannelGroup
  ): F[PeerManager[F]] =
    for {
      keyPair <- config.nodekeyOrPath match {
        case Left(kp)    => F.pure(kp)
        case Right(path) => loadOrGenerateNodeKey[F](path)
      }
      incoming     <- Ref.of[F, Map[KeyPair.Public, Peer[F]]](Map.empty)
      outgoing     <- Ref.of[F, Map[KeyPair.Public, Peer[F]]](Map.empty)
      nodeQueue    <- PriorityQueue.bounded[F, PeerNode](maxQueueSize)
      messageQueue <- Queue.bounded[F, Request[F]](maxQueueSize)
    } yield
      new PeerManager[F](config, keyPair, history, incoming, outgoing, nodeQueue, messageQueue) {
        private[jbok] def handshakeIncoming(conn: Connection[F, Message]): F[Peer[F]] =
          for {
            handshaker   <- AuthHandshaker[F](keyPair)
            result       <- handshaker.accept(conn)
            localStatus  <- localStatus
            _            <- conn.write(localStatus)
            remoteStatus <- conn.read.map(_.asInstanceOf[Status])
            _ <- if (!localStatus.isCompatible(remoteStatus)) {
              F.raiseError(PeerErr.Incompatible(localStatus, remoteStatus))
            } else {
              F.unit
            }
            peer <- Peer[F](KeyPair.Public(result.remotePubKey), conn, remoteStatus)
          } yield peer

        private[jbok] def handshakeOutgoing(conn: Connection[F, Message], remotePk: KeyPair.Public): F[Peer[F]] =
          for {
            handshaker   <- AuthHandshaker[F](keyPair)
            result       <- handshaker.connect(conn, remotePk)
            localStatus  <- localStatus
            _            <- conn.write(localStatus)
            remoteStatus <- conn.read.map(_.asInstanceOf[Status])
            _ <- if (!localStatus.isCompatible(remoteStatus)) {
              F.raiseError(PeerErr.Incompatible(localStatus, remoteStatus))
            } else {
              F.unit
            }
            peer <- Peer[F](KeyPair.Public(result.remotePubKey), conn, remoteStatus)
          } yield peer
      }
}
