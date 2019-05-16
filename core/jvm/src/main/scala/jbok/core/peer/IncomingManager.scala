package jbok.core.peer

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import cats.effect._
import cats.effect.concurrent.{Deferred, Ref}
import cats.effect.implicits._
import cats.implicits._
import fs2._
import fs2.io.tcp.Socket
import jbok.codec.rlp.implicits._
import jbok.common.log.Logger
import jbok.core.config.Configs.PeerConfig
import jbok.core.ledger.History
import jbok.core.messages.Status
import jbok.core.queue.Queue
import jbok.core.peer.handshake.AuthHandshaker
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import jbok.network.tcp.implicits._
import jbok.network.{Message, Request}

final class IncomingManager[F[_]](history: History[F], config: PeerConfig, val inbound: Queue[F, Peer[F], Message[F]])(
    implicit F: ConcurrentEffect[F],
    cs: ContextShift[F],
    T: Timer[F],
    acg: AsynchronousChannelGroup
) {
  private val log = Logger[F]

  val connected: Ref[F, Map[String, (Peer[F], Socket[F])]] = Ref.unsafe(Map.empty)

  def close(uri: PeerUri): F[Unit] =
    connected.get.map(_.get(uri.toString)).flatMap {
      case Some((_, socket)) => socket.endOfOutput >> socket.close
      case _                 => F.unit
    }

  val localBindAddress: Deferred[F, InetSocketAddress] = Deferred.unsafe[F, InetSocketAddress]

  val localStatus: F[Status] =
    for {
      genesis <- history.genesisHeader
      number  <- history.getBestBlockNumber
    } yield Status(history.chainId, genesis.hash, number)

  def handshake(socket: Socket[F], keyPair: KeyPair): F[Peer[F]] =
    for {
      handshaker  <- AuthHandshaker[F](keyPair)
      result      <- handshaker.accept(socket)
      localStatus <- localStatus
      request = Request.binary[F, Status](Status.name, localStatus.asValidBytes)
      _            <- socket.writeMessage(request)
      remoteStatus <- socket.readMessage.flatMap(_.as[Status])
      remote       <- socket.remoteAddress.map(_.asInstanceOf[InetSocketAddress])
      uri = PeerUri.fromTcpAddr(KeyPair.Public(result.remotePubKey), remote)
//      _ <- if (!localStatus.isCompatible(remoteStatus)) {
//        F.raiseError(PeerErr.Incompatible(localStatus, remoteStatus))
//      } else {
//        F.unit
//      }
      peer <- Peer[F](uri, remoteStatus)
      _    <- log.i(s"accepted incoming peer=${peer.uri}")
    } yield peer

  val keyPair: KeyPair = {
    val secret = KeyPair.Secret(config.secret)
    val public = Signature[ECDSA].generatePublicKey[IO](secret).unsafeRunSync()
    KeyPair(public, secret)
  }

  val localPeerUri: F[PeerUri] = localBindAddress.get.map(addr => PeerUri.fromTcpAddr(keyPair.public, addr))

  val peers: Stream[F, Resource[F, (Peer[F], Socket[F])]] =
    Socket
      .serverWithLocalAddress[F](
        address = config.bindAddr,
        maxQueued = 10,
        reuseAddress = true,
        receiveBufferSize = config.maxBufferSize
      )
      .flatMap {
        case Left(bound) => Stream.eval_(localBindAddress.complete(bound))
        case Right(res) =>
          Stream.emit {
            for {
              socket    <- res
              tlsSocket <- Resource.liftF(socket.toTLSSocket)
              peer      <- Resource.liftF(handshake(socket, keyPair))
              _ <- Resource.make(connected.update(_ + (peer.uri.uri.toString -> (peer -> socket))).as(peer))(peer =>
                log.i("close incoming") >> connected.update(_ - peer.uri.uri.toString))
            } yield (peer, tlsSocket)
          }
      }

  val serve: Stream[F, Unit] =
    peers
      .map { res =>
        Stream
          .resource(res)
          .flatMap {
            case (peer, socket) =>
              Stream(
                socket
                  .reads(config.maxBufferSize, None)
                  .through(Message.decodePipe[F])
                  .map(m => peer -> m)
                  .through(inbound.sink)
                  .onFinalize(log.i(s"finalize ${peer}") >> connected.update(_ - peer.uri.uri.toString)),
                peer.queue.dequeue.through(Message.encodePipe[F]).through(socket.writes(None))
              ).parJoinUnbounded
          }
          .handleErrorWith(e => Stream.eval(log.w("", e)))
      }
      .parJoin(config.maxIncomingPeers)

  val resource: Resource[F, PeerUri] = Resource {
    for {
      fiber   <- serve.compile.drain.start
      address <- localBindAddress.get
      _       <- log.i(s"successfully bound to ${address}")
    } yield PeerUri.fromTcpAddr(keyPair.public, address) -> fiber.cancel
  }
}
