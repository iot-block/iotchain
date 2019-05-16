package jbok.core.peer

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import cats.effect._
import cats.effect.concurrent.{Deferred, Ref}
import cats.effect.implicits._
import cats.implicits._
import fs2._
import fs2.io.tcp.Socket
import javax.net.ssl.SSLContext
import jbok.codec.rlp.implicits._
import jbok.common.log.Logger
import jbok.core.config.PeerConfig
import jbok.core.ledger.History
import jbok.core.messages.Status
import jbok.core.queue.Queue
import jbok.network.tcp.implicits._
import jbok.network.{Message, Request}

final class IncomingManager[F[_]](history: History[F], config: PeerConfig, val inbound: Queue[F, Peer[F], Message[F]], ssl: Option[SSLContext])(
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
      td      <- history.getTotalDifficultyByNumber(number).map(_.getOrElse(BigInt(0)))
      service = s"https://${config.host}:${config.port + 1}"
    } yield Status(history.chainId, genesis.hash, number, td, service)

  def handshake(socket: Socket[F]): F[Peer[F]] =
    for {
//      handshaker  <- AuthHandshaker[F](keyPair)
//      result      <- handshaker.accept(socket)
      localStatus <- localStatus
      request = Request.binary[F, Status](Status.name, localStatus.asValidBytes)
      _            <- socket.writeMessage(request)
      remoteStatus <- socket.readMessage.flatMap(_.as[Status])
      remote       <- socket.remoteAddress.map(_.asInstanceOf[InetSocketAddress])
      uri = PeerUri.fromTcpAddr(remote)
      //      uri = PeerUri.fromTcpAddr(KeyPair.Public(result.remotePubKey), remote)
//      _ <- if (!localStatus.isCompatible(remoteStatus)) {
//        F.raiseError(PeerErr.Incompatible(localStatus, remoteStatus))
//      } else {
//        F.unit
//      }
      peer <- Peer[F](uri, remoteStatus)
      _    <- log.i(s"accepted incoming peer=${peer.uri}")
    } yield peer

  val localPeerUri: F[PeerUri] = localBindAddress.get.map(addr => PeerUri.fromTcpAddr(addr))

  val peers: Stream[F, Resource[F, (Peer[F], Socket[F])]] =
    Socket
      .serverWithLocalAddress[F](
        address = config.bindAddr,
        maxQueued = 10,
        reuseAddress = true,
        receiveBufferSize = config.maxBufferSize
      )
      .flatMap {
        case Left(bound) =>
          Stream.eval_(log.i(s"IncomingManager successfully bound to address ${bound}") >> localBindAddress.complete(bound))
        case Right(res) =>
          Stream.emit {
            for {
              socket    <- res
              tlsSocket <- Resource.liftF(socket.toTLSSocket(ssl, client = false))
              peer      <- Resource.liftF(handshake(socket))
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
                  .onFinalize(log.i(s"finalize ${peer.uri.address}") >> connected.update(_ - peer.uri.uri.toString)),
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
    } yield PeerUri.fromTcpAddr(address) -> fiber.cancel
  }
}
