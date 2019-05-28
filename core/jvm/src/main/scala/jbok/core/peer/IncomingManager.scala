package jbok.core.peer

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import cats.effect._
import cats.effect.concurrent.Deferred
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

final class IncomingManager[F[_]](config: FullConfig, history: History[F], ssl: Option[SSLContext], val inbound: Queue[F, Peer[F], Message[F]])(
    implicit F: ConcurrentEffect[F],
    cs: ContextShift[F],
    acg: AsynchronousChannelGroup
) extends BaseManager[F](config, history) {
  private val log = Logger[F]

  val localBindAddress: Deferred[F, InetSocketAddress] = Deferred.unsafe[F, InetSocketAddress]

  val localPeerUri: F[PeerUri] = localBindAddress.get.map(addr => PeerUri.fromTcpAddr(addr))

  val peers: Stream[F, Resource[F, (Peer[F], Socket[F])]] =
    Socket
      .serverWithLocalAddress[F](
        address = config.peer.bindAddr,
        maxQueued = 10,
        reuseAddress = true,
        receiveBufferSize = config.peer.bufferSize
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
              _         <- Resource.make(connected.update(_ + (peer.uri -> (peer -> socket))).as(peer))(peer => connected.update(_ - peer.uri))
              _         <- Resource.liftF(log.i(s"accepted incoming peer ${peer.uri}"))
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
                  .reads(config.peer.bufferSize, None)
                  .through(Message.decodePipe[F])
                  .map(m => peer -> m)
                  .through(inbound.sink)
                  .onFinalize(log.i(s"disconnected incoming ${peer.uri}") >> connected.update(_ - peer.uri)),
                peer.queue.dequeue.through(Message.encodePipe[F]).through(socket.writes(None))
              ).parJoinUnbounded
          }
          .handleErrorWith(e => Stream.eval(log.w(s"handle incoming peer failure: ${e}", e)))
      }
      .parJoin(config.peer.maxIncomingPeers)

  val resource: Resource[F, PeerUri] = Resource {
    for {
      fiber   <- serve.compile.drain.start
      address <- localBindAddress.get
    } yield PeerUri.fromTcpAddr(address) -> fiber.cancel
  }
}
