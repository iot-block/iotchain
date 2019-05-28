package jbok.core.peer

import java.nio.channels.AsynchronousChannelGroup

import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import fs2._
import fs2.io.tcp.Socket
import javax.net.ssl.SSLContext
import jbok.common.log.Logger
import jbok.core.config.FullConfig
import jbok.core.ledger.History
import jbok.core.queue.Queue
import jbok.network.Message
import jbok.network.tcp.implicits._

import scala.concurrent.duration._

final class OutgoingManager[F[_]](config: FullConfig, history: History[F], ssl: Option[SSLContext], val inbound: Queue[F, Peer[F], Message[F]], val store: PeerStore[F])(
    implicit F: ConcurrentEffect[F],
    cs: ContextShift[F],
    T: Timer[F],
    acg: AsynchronousChannelGroup
) extends BaseManager[F](config, history) {
  private val log = Logger[F]

  private[peer] def connectTo(peerUri: PeerUri): Resource[F, (Peer[F], Socket[F])] =
    for {
      _ <- Resource.liftF(log.i(s"connecting to ${peerUri.address}"))
      socket <- Socket
        .client[F](
          to = peerUri.address,
          reuseAddress = true,
          sendBufferSize = config.peer.bufferSize,
          receiveBufferSize = config.peer.bufferSize,
          keepAlive = true,
          noDelay = true
        )
      tlsSocket <- Resource.liftF(socket.toTLSSocket(ssl, client = true))
      peer      <- Resource.liftF(handshake(socket))
      _         <- Resource.make(connected.update(_ + (peer.uri -> (peer -> socket))).as(peer))(peer => connected.update(_ - peer.uri))
    } yield peer -> tlsSocket

  private[peer] def connect(peerUri: PeerUri): Stream[F, Unit] =
    Stream
      .resource(connectTo(peerUri))
      .flatMap {
        case (peer, socket) =>
          Stream(
            socket
              .reads(config.peer.bufferSize, None)
              .through(Message.decodePipe[F])
              .map(m => peer -> m)
              .through(inbound.sink)
              .onFinalize(log.i(s"disconnected outgoing ${peer.uri}") >> connected.update(_ - peer.uri)),
            peer.queue.dequeue.through(Message.encodePipe).through(socket.writes(None))
          ).parJoinUnbounded
      }
      .handleErrorWith(e => Stream.sleep(15.seconds) ++ connect(peerUri))

  val connects: Stream[F, Unit] =
    Stream.eval(store.add(config.peer.seedUris: _*)) ++
      store.subscribe
        .map(uri => connect(uri))
        .parJoin(config.peer.maxOutgoingPeers)

  val resource: Resource[F, Fiber[F, Unit]] = Resource {
    connects.compile.drain.start.map { fiber =>
      fiber -> fiber.cancel
    }
  }
}
