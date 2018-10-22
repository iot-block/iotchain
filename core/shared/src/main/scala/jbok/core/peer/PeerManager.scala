package jbok.core.peer

import java.net.{InetSocketAddress, URI}

import cats.effect.{ConcurrentEffect, Fiber, Timer}
import cats.implicits._
import fs2._
import fs2.async.Ref
import jbok.core.config.Configs.{PeerManagerConfig, SyncConfig}
import jbok.core.messages.{Message, Messages, Status, SyncMessage}
import jbok.core.peer.PeerEvent.PeerRecv
import jbok.core.peer.discovery.Discovery
import jbok.core.sync.SyncService
import jbok.core.{History, NodeStatus}
import jbok.network.Connection
import jbok.network.common.{RequestId, RequestMethod}
import jbok.network.execution._
import jbok.network.transport.{TcpTransport, TransportEvent}
import scodec.bits.BitVector
import scodec.{Attempt, Codec, DecodeResult, Decoder, Encoder, SizeBound}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

trait PeerManager[F[_]] {
  def localAddress: F[InetSocketAddress]

  def localStatus: F[Status]

  def knownPeers: F[Set[PeerNode]]

  def discoveredPeers: Stream[F, Set[PeerNode]]

  def handshakedPeers: F[Map[PeerId, HandshakedPeer]]

  def listen: F[Unit]

  def stop: F[Unit]

  def connect(to: InetSocketAddress): F[Unit]

  def handshake(conn: Connection[F, Message]): F[Unit]

  def broadcast(msg: Message): F[Unit]

  def sendMessage(peerId: PeerId, msg: Message): F[Unit]

  def sendRequest(peerId: PeerId, msg: Message, timeout: Option[FiniteDuration] = None): F[Option[Message]]

  def subscribe(): Stream[F, PeerEvent]

  def subscribeMessages(): Stream[F, PeerRecv]

  def ban(peerId: PeerId, duration: FiniteDuration, reason: String): F[Unit]

  def unban(peerId: PeerId): F[Unit]

  def isBanned(peerId: PeerId): F[Boolean]
}

class PeerManagerImpl[F[_]](
    config: PeerManagerConfig,
    history: History[F],
    transport: TcpTransport[F, Message],
    known: PeerNodeManager[F],
    discovery: Discovery[F],
    handshaked: Ref[F, Map[PeerId, HandshakedPeer]],
    banned: Ref[F, Map[PeerId, Fiber[F, Unit]]]
)(implicit F: ConcurrentEffect[F], T: Timer[F], EC: ExecutionContext)
    extends PeerManager[F] {

  private[this] val log = org.log4s.getLogger

  override def localAddress: F[InetSocketAddress] = {
    val bind = new InetSocketAddress(config.bindAddr.host, config.bindAddr.port.get)
    F.pure(bind)
  }

  override def localStatus: F[Status] =
    for {
      genesis   <- history.genesisHeader
      number    <- history.getBestBlockNumber
      headerOpt <- history.getBlockHeaderByNumber(number)
      header = headerOpt.getOrElse(genesis)
      tdOpt <- history.getTotalDifficultyByHash(header.hash)
      td = tdOpt.getOrElse(BigInt(0))
    } yield Status(1, genesis.hash, header.hash, number, td)

  override def knownPeers: F[Set[PeerNode]] =
    known.getAll

  override def discoveredPeers: Stream[F, Set[PeerNode]] =
    ???

  override def handshakedPeers: F[Map[PeerId, HandshakedPeer]] =
    handshaked.get

  override def listen: F[Unit] =
    for {
      local <- localAddress
      _     <- transport.listen(local, handshake, config.maxIncomingPeers)
    } yield ()

  override def stop: F[Unit] =
    transport.stop

  override def connect(to: InetSocketAddress): F[Unit] =
    transport.connect(to, handshake)

  override def handshake(conn: Connection[F, Message]): F[Unit] =
    for {
      localStatus <- localStatus
      remote      <- conn.remoteAddress
      _           <- conn.write(localStatus, Some(config.handshakeTimeout))
      peerOpt <- conn.read(timeout = Some(config.handshakeTimeout)).map {
        case Some(status: Status) => Some(HandshakedPeer(remote, PeerInfo(status), conn.isIncoming))
        case _                    => None
      }
      _ <- peerOpt match {
        case None =>
          log.info(s"${remote} handshake failed, close")
          conn.close
        case Some(peer) =>
          log.info(s"${remote} with ${peer.peerInfo.status} handshake succeed")
          handshaked.modify(_ + (peer.peerId -> peer))
      }
    } yield ()

  override def broadcast(msg: Message): F[Unit] =
    for {
      peers <- handshakedPeers.map(_.values.toList)
      _ = log.info(s"broadcast ${msg.name} to ${peers.length} peers")
      _ <- peers.traverse(peer => transport.write(peer.remote, msg))
    } yield ()

  override def sendMessage(peerId: PeerId, msg: Message): F[Unit] =
    for {
      peers <- handshakedPeers
      _ <- peers.get(peerId) match {
        case Some(peer) =>
          log.info(s"sending ${msg.name} to ${peerId}")
          transport.write(peer.remote, msg).attempt.map {
            case Left(e)  => log.error(e)("sendMessage error")
            case Right(_) => ()
          }
        case None =>
          log.info(s"${peerId} already dropped")
          F.unit
      }
    } yield ()

  override def sendRequest(
      peerId: PeerId,
      msg: Message,
      timeout: Option[FiniteDuration]
  ): F[Option[Message]] =
    for {
      peers <- handshakedPeers
      msg <- peers.get(peerId) match {
        case Some(peer) =>
          log.info(s"sending request to ${peerId}")
          transport.request(peer.remote, msg)
        case None =>
          log.info(s"${peerId} has already disconnected")
          F.pure(None)
      }
    } yield msg

  override def subscribe(): Stream[F, PeerEvent] =
    transport.subscribe().map {
      case TransportEvent.Received(remote, msg) =>
        PeerEvent.PeerRecv(PeerId(remote.toString), msg)

      case TransportEvent.Add(remote, _) =>
        PeerEvent.PeerAdd(PeerId(remote.toString))

      case TransportEvent.Drop(remote, _) =>
        PeerEvent.PeerDrop(PeerId(remote.toString))
    }

  override def subscribeMessages(): Stream[F, PeerRecv] =
    subscribe().collect {
      case x: PeerEvent.PeerRecv => x
    }

  override def ban(peerId: PeerId, duration: FiniteDuration, reason: String): F[Unit] =
    for {
      m     <- banned.get
      fiber <- F.start(T.sleep(duration) *> banned.modify(_ - peerId).void)
      _ <- m.get(peerId) match {
        case Some(f) => f.cancel
        case None =>
          banned.modify(_ + (peerId -> fiber))
      }
    } yield ()

  override def unban(peerId: PeerId): F[Unit] =
    banned.modify(_ - peerId).void

  override def isBanned(peerId: PeerId): F[Boolean] =
    banned.get.map(_.contains(peerId))
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

  def emptyPipe[F[_]]: Pipe[F, Message, Message] = _.flatMap(_ => Stream.empty.covary[F])

  def apply[F[_]](
      peerConfig: PeerManagerConfig,
      syncConfig: SyncConfig,
      history: History[F]
  )(implicit F: ConcurrentEffect[F], T: Timer[F], EC: ExecutionContext): F[PeerManager[F]] = {
    val syncService = SyncService[F](syncConfig, history)
    for {
      nodeManager <- PeerNodeManager[F](history.db)
//      discovery <- Discovery[F](DiscoveryConfig(), nodeStatus)
      discovery = null
      transport  <- TcpTransport(syncService.pipe, peerConfig.timeout)
      handshaked <- fs2.async.refOf[F, Map[PeerId, HandshakedPeer]](Map.empty)
      banned     <- fs2.async.refOf[F, Map[PeerId, Fiber[F, Unit]]](Map.empty)
    } yield new PeerManagerImpl[F](peerConfig, history, transport, nodeManager, discovery, handshaked, banned)
  }
}
