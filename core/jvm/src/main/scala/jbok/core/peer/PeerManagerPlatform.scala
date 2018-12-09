package jbok.core.peer

import java.nio.channels.AsynchronousChannelGroup

import better.files._
import cats.data.OptionT
import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, ContextShift, IO, Timer}
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
  private[this] val log = org.log4s.getLogger("PeerManager")

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
      config: PeerConfig,
      keyPairOpt: Option[KeyPair],
      history: History[F],
      maxQueueSize: Int = 64
  )(
      implicit F: ConcurrentEffect[F],
      CS: ContextShift[F],
      T: Timer[F],
      AG: AsynchronousChannelGroup,
      chainId: BigInt
  ): F[PeerManager[F]] =
    for {
      keyPair      <- OptionT.fromOption[F](keyPairOpt).getOrElseF(F.liftIO(loadOrGenerateNodeKey(config.nodekeyPath)))
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
              F.raiseError(PeerErr.Incompatible)
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
              F.raiseError(PeerErr.Incompatible)
            } else {
              F.unit
            }
            peer <- Peer[F](KeyPair.Public(result.remotePubKey), conn, remoteStatus)
          } yield peer
      }
}
