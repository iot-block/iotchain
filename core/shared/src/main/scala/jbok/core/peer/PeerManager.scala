package jbok.core.peer
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import cats.data.OptionT
import cats.effect.concurrent.Ref
import cats.effect.implicits._
import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import cats.implicits._
import fs2.Stream._
import fs2._
import fs2.concurrent.Queue
import jbok.common.concurrent.PriorityQueue
import jbok.core.config.Configs.PeerConfig
import jbok.core.ledger.History
import jbok.core.messages._
import jbok.core.peer.PeerSelectStrategy.PeerSelectStrategy
import jbok.crypto.signature.KeyPair
import jbok.network.Connection
import jbok.network.common.TcpUtil

sealed abstract class PeerErr(message: String) extends Exception(message)
object PeerErr {
  case object HandshakeTimeout extends PeerErr("handshake timeout")
  case object Incompatible     extends PeerErr("incompatible peer")
}

abstract class PeerManager[F[_]](
    val config: PeerConfig,
    val keyPair: KeyPair,
    val history: History[F],
    val incoming: Ref[F, Map[KeyPair.Public, Peer[F]]],
    val outgoing: Ref[F, Map[KeyPair.Public, Peer[F]]],
    val nodeQueue: PriorityQueue[F, PeerNode],
    val messageQueue: Queue[F, Request[F]]
)(implicit F: ConcurrentEffect[F], CS: ContextShift[F], T: Timer[F], AG: AsynchronousChannelGroup, chainId: BigInt) {
  private[this] val log = org.log4s.getLogger("PeerManager")

  val peerNode: PeerNode = PeerNode(keyPair.public, config.host, config.port, config.discoveryPort)

  def listen(
      bind: InetSocketAddress = config.bindAddr,
      maxQueued: Int = config.maxPendingPeers,
      maxOpen: Int = config.maxIncomingPeers
  ): Stream[F, Unit] =
    fs2.io.tcp.Socket
      .serverWithLocalAddress[F](bind, maxQueued)
      .map {
        case Left(bound) =>
          Stream.eval(F.delay(log.info(s"Peer(${peerNode.id.toHex.take(7)}) successfully bound to ${bound}")))

        case Right(res) =>
          val stream = for {
            conn <- eval(TcpUtil.socketToConnection[F, Message](res, true))
            _    <- eval(conn.start)
            peer <- eval(
              handshakeIncoming(conn).timeoutTo(config.handshakeTimeout, F.raiseError(PeerErr.HandshakeTimeout)))
            _ = log.debug(s"${peer.id} handshaked")
            _ <- eval(incoming.update(_ + (peer.pk -> peer)))
            _ <- peer.conn.reads
              .map(msg => Request(peer, msg))
              .to(messageQueue.enqueue)
              .onFinalize(incoming.update(_ - peer.pk) >> F.delay(log.debug(s"${peer.id} disconnected")))
          } yield ()

          stream.handleErrorWith {
            case PeerErr.HandshakeTimeout => Stream.eval(F.delay(log.warn("timeout")))
            case PeerErr.Incompatible     => Stream.eval(F.delay(log.warn("incompatible")))
            case e =>
              log.error(e)("unexpected listen error")
              Stream.raiseError[F](e)
          }
      }
      .parJoin(maxOpen)

  def connect(maxOpen: Int = config.maxOutgoingPeers): Stream[F, Unit] =
    nodeQueue.dequeue
      .map(node => connect(node))
      .parJoin(maxOpen)

  private def connect(
      to: PeerNode,
  ): Stream[F, Unit] = {
    val connect0 = {
      val res = io.tcp.Socket.client[F](to.tcpAddress, keepAlive = true, noDelay = true)
      val stream = for {
        conn <- eval(TcpUtil.socketToConnection[F, Message](res, false))
        _    <- eval(conn.start)
        peer <- eval(
          handshakeOutgoing(conn, to.pk).timeoutTo(config.handshakeTimeout, F.raiseError(PeerErr.HandshakeTimeout)))
        _ = log.debug(s"${peer.id} handshaked")
        _ <- eval(outgoing.update(_ + (peer.pk -> peer)))
        _ <- peer.conn.reads
          .map(msg => Request(peer, msg))
          .to(messageQueue.enqueue)
          .onFinalize(outgoing.update(_ - peer.pk) >> F.delay(log.debug(s"${peer.id} disconnected")))
      } yield ()

      stream.handleErrorWith {
        case PeerErr.HandshakeTimeout => Stream.eval(F.delay(log.warn("timeout")))
        case PeerErr.Incompatible     => Stream.eval(F.delay(log.warn("incompatible")))
        case e =>
          log.error(e)("unexpected connect error")
          Stream.raiseError[F](e)
      }
    }

    Stream.eval(outgoing.get.map(_.contains(to.pk))).flatMap {
      case true =>
        log.debug(s"already connected, ignore")
        Stream.empty.covary[F]

      case false => connect0
    }
  }

  def stream: Stream[F, Unit] =
    listen().concurrently(connect())

  def addPeerNode(nodes: PeerNode*): F[Unit] =
    nodes.toList
      .filterNot(_.tcpAddress == config.bindAddr)
      .distinct
      .traverse(node => nodeQueue.enqueue1(node, node.peerType.priority))
      .void

  def close(pk: KeyPair.Public): F[Unit] =
    getPeer(pk).semiflatMap(_.conn.close).getOrElseF(F.unit)

  def connected: F[List[Peer[F]]] =
    for {
      in  <- incoming.get
      out <- outgoing.get
    } yield (in ++ out).values.toList

  def distribute(strategy: PeerSelectStrategy[F], message: Message): F[Unit] =
    for {
      peers    <- connected
      selected <- strategy.run(peers)
      _        <- selected.traverse(_.conn.write(message))
    } yield ()

  private[jbok] def localStatus: F[Status] =
    for {
      genesis <- history.genesisHeader
      number  <- history.getBestBlockNumber
    } yield Status(chainId, genesis.hash, number)

  private[jbok] def handshakeIncoming(conn: Connection[F, Message]): F[Peer[F]]

  private[jbok] def handshakeOutgoing(conn: Connection[F, Message], remotePk: KeyPair.Public): F[Peer[F]]

  private[jbok] def getPeer(pk: KeyPair.Public): OptionT[F, Peer[F]] =
    OptionT(incoming.get.map(_.get(pk))).orElseF(outgoing.get.map(_.get(pk)))
}
