package jbok.core.peer

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import cats.effect._
import cats.effect.concurrent.Ref
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

import scala.concurrent.duration._

final class OutgoingManager[F[_]](config: PeerConfig, history: History[F], val store: PeerStore[F], val inbound: Queue[F, Peer[F], Message[F]], ssl: Option[SSLContext])(
    implicit F: ConcurrentEffect[F],
    cs: ContextShift[F],
    T: Timer[F],
    acg: AsynchronousChannelGroup
) {
  private val log = Logger[F]

  val connected: Ref[F, Map[String, (Peer[F], Socket[F])]] = Ref.unsafe(Map.empty)

  private[peer] val localStatus: F[Status] =
    for {
      genesis <- history.genesisHeader
      number  <- history.getBestBlockNumber
      td      <- history.getTotalDifficultyByNumber(number).map(_.getOrElse(BigInt(0)))
      service = s"https://${config.host}:${config.port + 1}"
    } yield Status(history.chainId, genesis.hash, number, td, service)

  private[peer] def handshake(socket: Socket[F]): F[Peer[F]] =
    for {
      localStatus <- localStatus
      request = Request.binary[F, Status](Status.name, localStatus.asValidBytes)
      _            <- socket.writeMessage(request)
      remoteStatus <- socket.readMessage.flatMap(_.as[Status])
      remote       <- socket.remoteAddress.map(_.asInstanceOf[InetSocketAddress])
      uri = PeerUri.fromTcpAddr(remote)
      _ <- if (!localStatus.isCompatible(remoteStatus)) {
        F.raiseError(new Exception("incompatible peer"))
      } else {
        F.unit
      }
      peer <- Peer[F](uri, remoteStatus)
      _    <- log.i(s"connected outgoing peer=${peer.uri}")
    } yield peer

  private[peer] def connectTo(peerUri: PeerUri): Resource[F, (Peer[F], Socket[F])] =
    for {
      _ <- Resource.liftF(log.i(s"connecting to ${peerUri.address}"))
      socket <- Socket
        .client[F](
          to = peerUri.address,
          reuseAddress = true,
          sendBufferSize = 4 * 1024 * 1024,
          receiveBufferSize = 4 * 1024 * 1024,
          keepAlive = true,
          noDelay = true
        )
      tlsSocket <- Resource.liftF(socket.toTLSSocket(ssl, client = true))
      peer      <- Resource.liftF(handshake(socket))
      _         <- Resource.make(connected.update(_ + (peer.uri.uri.toString -> (peer -> socket))).as(peer))(peer => log.i("close out") >> connected.update(_ - peer.uri.uri.toString))
    } yield peer -> tlsSocket

  private[peer] def connect(peerUri: PeerUri): Stream[F, Unit] =
    Stream
      .resource(connectTo(peerUri))
      .flatMap {
        case (peer, socket) =>
          Stream(
            socket
              .reads(config.maxBufferSize, None)
              .through(Message.decodePipe[F])
              .map(m => peer -> m)
              .through(inbound.sink)
              .onFinalize(log.i(s"disconnected ${peer.uri.address}") >> connected.update(_ - peer.uri.uri.toString)),
            peer.queue.dequeue.through(Message.encodePipe).through(socket.writes(None))
          ).parJoinUnbounded
      }
      .handleErrorWith(e => Stream.sleep(15.seconds) ++ connect(peerUri))

  def close(uri: PeerUri): F[Unit] =
    connected.get.map(_.get(uri.uri.toString).map(_._2)).flatMap {
      case Some(socket) => socket.endOfOutput >> socket.close
      case _            => F.unit
    }

  val connects: Stream[F, Unit] =
    Stream.eval(store.add(config.seedUris: _*)) ++
      store.subscribe
        .map(uri => connect(uri))
        .parJoin(config.maxOutgoingPeers)

  val resource: Resource[F, Fiber[F, Unit]] = Resource {
    connects.compile.drain.start.map { fiber =>
      fiber -> fiber.cancel
    }
  }
}
