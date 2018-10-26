package jbok.core.peer
import java.net.InetSocketAddress
import java.nio.channels.{AsynchronousChannelGroup, AsynchronousCloseException, ClosedChannelException}

import cats.data.OptionT
import cats.effect.concurrent.Ref
import cats.effect.implicits._
import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import fs2._
import fs2.concurrent.{Queue, SignallingRef, Topic}
import jbok.core.History
import jbok.core.config.Configs.{PeerManagerConfig, SyncConfig}
import jbok.core.messages.{Message, Messages, Status, SyncMessage}
import jbok.core.sync.SyncService
import jbok.network.Connection
import jbok.network.common.{RequestId, RequestMethod, TcpUtil}
import scodec._
import scodec.bits.BitVector

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class PeerManager[F[_]](
    val config: PeerManagerConfig,
    val history: History[F],
    val incoming: Ref[F, Map[InetSocketAddress, HandshakedPeer[F]]],
    val outgoing: Ref[F, Map[InetSocketAddress, HandshakedPeer[F]]],
    val dialQueue: Queue[F, InetSocketAddress],
    val messages: Topic[F, Option[(HandshakedPeer[F], Message)]],
    val pipe: Pipe[F, Message, Message],
    val haltWhenTrue: SignallingRef[F, Boolean]
)(implicit F: ConcurrentEffect[F],
  C: Codec[Message],
  I: RequestId[Message],
  T: Timer[F],
  AG: AsynchronousChannelGroup,
  EC: ExecutionContext) {
  private[this] val log = org.log4s.getLogger

  def handleError(e: Throwable): Stream[F, Unit] = e match {
    case _: AsynchronousCloseException | _: ClosedChannelException =>
      Stream.empty.covary[F]
    case _ =>
      Stream.eval(F.delay(log.error(e)("connection error")))
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
            conn   <- Stream.eval(TcpUtil.socketToConnection[F, Message](socket, true))
            _ = log.info(s"${conn} established")
            peer <- Stream.eval(handshake(conn))
            _ <- conn
              .reads()
              .evalMap(a => messages.publish1(Some(peer -> a)).map(_ => a))
              .through(pipe)
              .to(conn.writes())
              .onFinalize(incoming.update(_ - conn.remoteAddress) *> F.delay(log.info(s"${conn} disconnected")))
              .handleErrorWith(handleError)
          } yield ()
      }
      .parJoin(maxOpen)

  def connect(maxOpen: Int = config.maxOutgoingPeers): Stream[F, Unit] =
    dialQueue.dequeue
      .map(addr => connect(addr))
      .parJoin(maxOpen)

  private def connect(
      to: InetSocketAddress
  ): Stream[F, Unit] = {
    val connect0 = for {
      socket <- Stream.resource(fs2.io.tcp.client[F](to, keepAlive = true, noDelay = true))
      conn   <- Stream.eval(TcpUtil.socketToConnection[F, Message](socket, false))
      _ = log.info(s"${conn} established")
      peer <- Stream.eval(handshake(conn))
      _ <- conn
        .reads()
        .evalMap(a => messages.publish1(Some(peer -> a)).map(_ => a))
        .through(pipe)
        .to(conn.writes())
        .onFinalize(outgoing.update(_ - conn.remoteAddress) *> F.delay(log.info(s"${conn} disconnected")))
        .handleErrorWith(handleError)
    } yield ()

    Stream.eval(outgoing.get.map(_.contains(to))).flatMap {
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
        haltWhenTrue.set(false) *> F.delay(log.info("start manager")) *>
          listen().concurrently(connect()).interruptWhen(haltWhenTrue).compile.drain.start.void
    }

  def stop: F[Unit] =
    haltWhenTrue.set(true)

  def addKnown(addrs: InetSocketAddress*): F[Unit] =
    addrs.toList
      .filterNot(_ == config.bindAddr)
      .distinct
      .traverse(addr => dialQueue.enqueue1(addr))
      .void

  def connected: F[List[HandshakedPeer[F]]] =
    for {
      in  <- incoming.get
      out <- outgoing.get
    } yield (in ++ out).values.toList

  private[jbok] def localStatus: F[Status] =
    for {
      genesis   <- history.genesisHeader
      number    <- history.getBestBlockNumber
      headerOpt <- history.getBlockHeaderByNumber(number)
      header = headerOpt.getOrElse(genesis)
      tdOpt <- history.getTotalDifficultyByHash(header.hash)
      td = tdOpt.getOrElse(BigInt(0))
    } yield Status(1, genesis.hash, header.hash, number, td)

  private[jbok] def handshake(conn: Connection[F, Message]): F[HandshakedPeer[F]] =
    for {
      localStatus  <- localStatus
      _            <- conn.write(localStatus, Some(config.handshakeTimeout))
      remoteStatus <- conn.read(Some(config.handshakeTimeout)).map(_.asInstanceOf[Status])
      peer         <- HandshakedPeer[F](conn, remoteStatus)
      _ <- if (conn.isIncoming) {
        incoming.update(_ + (conn.remoteAddress -> peer))
      } else {
        outgoing.update(_ + (peer.conn.remoteAddress -> peer))
      }
    } yield peer

  private[jbok] def getPeer(remote: InetSocketAddress): OptionT[F, HandshakedPeer[F]] =
    OptionT(incoming.get.map(_.get(remote)))
      .orElseF(outgoing.get.map(_.get(remote)))

  def close(remote: InetSocketAddress): F[Unit] =
    getPeer(remote).semiflatMap(_.conn.close).getOrElseF(F.unit)

  def write(remote: InetSocketAddress, msg: Message, timeout: Option[FiniteDuration] = None): F[Unit] =
    getPeer(remote).semiflatMap(_.conn.write(msg, timeout)).getOrElseF(F.unit)

  def broadcast(msg: Message, timeout: Option[FiniteDuration] = None): F[Unit] =
    for {
      in  <- incoming.get
      out <- outgoing.get
      _   <- (in ++ out).values.toList.traverse(_.conn.write(msg, timeout))
    } yield ()

  def subscribe: Stream[F, (HandshakedPeer[F], Message)] =
    messages.subscribe(64).unNone
}

object PeerManager {
  implicit val I: RequestId[Message] = new RequestId[Message] {
    override def id(a: Message): Option[String] = a match {
      case x: SyncMessage => Some(x.id)
      case _              => None
    }
  }

  implicit val M: RequestMethod[Message] = new RequestMethod[Message] {
    override def method(a: Message): Option[String] = Some(a.name)
  }

  implicit val encoder: Encoder[Message] = new Encoder[Message] {
    override def encode(value: Message): Attempt[BitVector] =
      Attempt.successful(Messages.encode(value).bits)

    override def sizeBound: SizeBound = SizeBound.unknown
  }

  implicit val decoder: Decoder[Message] = new Decoder[Message] {
    override def decode(bits: BitVector): Attempt[DecodeResult[Message]] =
      Messages.decode(bits.bytes).map(message => DecodeResult(message, BitVector.empty))
  }

  implicit val codec: Codec[Message] = Codec(encoder, decoder)

  def apply[F[_]](
      config: PeerManagerConfig,
      sync: SyncConfig,
      history: History[F],
      maxQueueSize: Int = 64
  )(
      implicit F: ConcurrentEffect[F],
      T: Timer[F],
      AG: AsynchronousChannelGroup,
      EC: ExecutionContext
  ): F[PeerManager[F]] =
    for {
      incoming  <- Ref.of[F, Map[InetSocketAddress, HandshakedPeer[F]]](Map.empty)
      outgoing  <- Ref.of[F, Map[InetSocketAddress, HandshakedPeer[F]]](Map.empty)
      dialQueue <- Queue.bounded[F, InetSocketAddress](maxQueueSize)
      messages  <- Topic[F, Option[(HandshakedPeer[F], Message)]](None)
      pipe = SyncService[F](sync, history).pipe
      haltWhenTrue <- SignallingRef[F, Boolean](true)
    } yield new PeerManager[F](config, history, incoming, outgoing, dialQueue, messages, pipe, haltWhenTrue)
}
