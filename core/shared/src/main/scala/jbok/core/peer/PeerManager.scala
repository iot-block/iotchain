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

class PeerManager[F[_]](
    val config: PeerManagerConfig,
    val keyPair: KeyPair,
    val history: History[F],
    val incoming: Ref[F, Map[InetSocketAddress, Peer[F]]],
    val outgoing: Ref[F, Map[InetSocketAddress, Peer[F]]],
    val nodeQueue: PriorityQueue[F, PeerNode],
    val messages: Topic[F, Option[(Peer[F], Message)]],
    val pipe: Pipe[F, Message, Message],
    val haltWhenTrue: SignallingRef[F, Boolean]
)(implicit F: ConcurrentEffect[F], T: Timer[F], AG: AsynchronousChannelGroup) {
  private[this] val log = org.log4s.getLogger

  val peerNode: PeerNode = PeerNode(keyPair.public, config.interface, config.port)

  def handleError(e: Throwable): Stream[F, Unit] = e match {
    case _: AsynchronousCloseException | _: ClosedChannelException =>
      Stream.empty.covary[F]
    case _ =>
      Stream.raiseError[F](e)
  }

  def listen(
      bind: InetSocketAddress = config.bindAddr,
      maxQueued: Int = config.maxPendingPeers,
      maxOpen: Int = config.maxIncomingPeers
  ): Stream[F, Unit] =
    fs2.io.tcp
      .serverWithLocalAddress[F](bind, maxQueued)
      .map {
        case Left(bound) =>
          Stream.eval(F.delay(log.info(s"successfully bound to ${bound}")))

        case Right(res) =>
          for {
            socket <- Stream.resource(res)
            conn   <- Stream.eval(TcpUtil.socketToConnection[F](socket, true))
            _ = log.info(s"${conn} established")
            peer <- Stream.eval(handshakeIncoming(conn))
            _    <- Stream.eval(incoming.update(_ + (conn.remoteAddress -> peer)))
            _ <- conn
              .readsAndResolve[Message]()
              .evalMap(a => messages.publish1(Some(peer -> a)).map(_ => a))
              .through(pipe)
              .to(conn.writes())
              .onFinalize(incoming.update(_ - conn.remoteAddress) *> F.delay(log.info(s"${conn} disconnected")))
              .handleErrorWith(handleError)
          } yield ()
      }
      .parJoin(maxOpen)

  def connect(maxOpen: Int = config.maxOutgoingPeers): Stream[F, Unit] =
    nodeQueue.dequeue
      .map(node => connect(node))
      .parJoin(maxOpen)

  private def connect(
      to: PeerNode,
  ): Stream[F, Unit] = {
    val connect0 = for {
      socket <- Stream.resource(fs2.io.tcp.client[F](to.addr, keepAlive = true, noDelay = true))
      conn   <- Stream.eval(TcpUtil.socketToConnection[F](socket, false))
      _ = log.info(s"${conn} established")
      peer <- Stream.eval(handshakeOutgoing(conn, to.pk))
      _    <- Stream.eval(outgoing.update(_ + (peer.conn.remoteAddress -> peer)))
      _ <- conn
        .reads[Message]()
        .evalMap(a => messages.publish1(Some(peer -> a)).map(_ => a))
        .through(pipe)
        .to(conn.writes())
        .onFinalize(outgoing.update(_ - conn.remoteAddress) *> F.delay(log.info(s"${conn} disconnected")))
        .handleErrorWith(handleError)
    } yield ()

    Stream.eval(outgoing.get.map(_.contains(to.addr))).flatMap {
      case true =>
        log.info(s"already connected, ignore")
        Stream.empty.covary[F]

      case false => connect0
    }
  }

  def start: F[Unit] =
    haltWhenTrue.get.flatMap {
      case false => F.unit
      case true =>
        haltWhenTrue.set(false) *>
          listen().concurrently(connect()).interruptWhen(haltWhenTrue).compile.drain.start.void
    }

  def stop: F[Unit] =
    haltWhenTrue.set(true)

  def addPeerNode(nodes: PeerNode*): F[Unit] =
    nodes.toList
      .filterNot(_.addr == config.bindAddr)
      .distinct
      .traverse(node => nodeQueue.enqueue1(node, node.peerType.priority))
      .void

  def connected: F[List[Peer[F]]] =
    for {
      in  <- incoming.get
      out <- outgoing.get
    } yield (in ++ out).values.toList

  private[jbok] def localStatus: F[Status] =
    for {
      genesis <- history.genesisHeader
      number  <- history.getBestBlockNumber
    } yield Status(history.chainId, genesis.hash, number)

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

  private[jbok] def getPeer(remote: InetSocketAddress): OptionT[F, Peer[F]] =
    OptionT(incoming.get.map(_.get(remote)))
      .orElseF(outgoing.get.map(_.get(remote)))

  def close(remote: InetSocketAddress): F[Unit] =
    getPeer(remote).semiflatMap(_.conn.close).getOrElseF(F.unit)

  def broadcast(msg: Message, timeout: Option[FiniteDuration] = None): F[Unit] =
    for {
      in  <- incoming.get
      out <- outgoing.get
      _   <- (in ++ out).values.toList.traverse(_.conn.write(msg, timeout))
    } yield ()

  def subscribe: Stream[F, (Peer[F], Message)] =
    messages.subscribe(64).unNone
}

object PeerManager {
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
    } yield new PeerManager[F](config, keyPair, history, incoming, outgoing, nodeQueue, messages, pipe, haltWhenTrue)
}
