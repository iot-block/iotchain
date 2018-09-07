package jbok.core.peer

import java.net.InetSocketAddress

import cats.effect.ConcurrentEffect
import cats.implicits._
import fs2._
import jbok.core.History
import jbok.core.Configs.PeerManagerConfig
import jbok.core.messages.{Message, Messages, Status}
import jbok.core.peer.PeerEvent.PeerRecv
import jbok.network.Connection
import jbok.network.common.{RequestId, RequestMethod}
import jbok.network.execution._
import jbok.network.transport.{TcpTransport, TransportEvent}
import scodec.bits.BitVector
import scodec.{Attempt, Codec, DecodeResult, Decoder, Encoder, SizeBound}

import scala.concurrent.ExecutionContext

trait PeerManager[F[_]] {
  def localAddress: F[InetSocketAddress]

  def status: F[Status]

  def handshakedPeers: F[Map[PeerId, HandshakedPeer]]

  def listen: F[Unit]

  def stop: F[Unit]

  def connect(to: InetSocketAddress): F[Unit]

  def handshake(conn: Connection[F, Message]): F[Unit]

  def broadcast(msg: Message): F[Unit]

  def sendMessage(peerId: PeerId, msg: Message): F[Unit]

  def subscribe(): Stream[F, PeerEvent]

  def subscribeMessages(): Stream[F, PeerRecv]
}

object PeerManager {
  private[this] val log = org.log4s.getLogger

  implicit val I: RequestId[Message] = new RequestId[Message] {
    override def id(a: Message): Option[String] = None
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

  def defaultPipe[F[_]]: Pipe[F, Message, Message] = _.map(identity)

  def apply[F[_]](
      config: PeerManagerConfig,
      history: History[F],
      pipe: Pipe[F, Message, Message] = defaultPipe[F]
  )(implicit F: ConcurrentEffect[F], EC: ExecutionContext): F[PeerManager[F]] =
    for {
      handshaked <- fs2.async.refOf[F, Map[PeerId, HandshakedPeer]](Map.empty)
      transport  <- TcpTransport(pipe, config.timeout)
    } yield
      new PeerManager[F] {
        override def localAddress: F[InetSocketAddress] = {
          val bind = new InetSocketAddress(config.bindAddr.host, config.bindAddr.port.get)
          F.pure(bind)
        }

        override def status: F[Status] =
          for {
            number    <- history.getBestBlockNumber
            headerOpt <- history.getBlockHeaderByNumber(number)
            genesis   <- history.genesisHeader
            header = headerOpt.getOrElse(genesis)
          } yield Status(1, 1, header.hash, genesis.hash)

        override def handshakedPeers: F[Map[PeerId, HandshakedPeer]] =
          handshaked.get

        override def listen: F[Unit] =
          for {
            local <- localAddress
            _     <- transport.listen(local, handshake)
          } yield ()

        override def stop: F[Unit] =
          transport.stop

        override def connect(to: InetSocketAddress): F[Unit] =
          transport.connect(to, handshake)

        override def handshake(conn: Connection[F, Message]): F[Unit] =
          for {
            localStatus <- status
            _           <- conn.write(localStatus, Some(config.handshakeTimeout))
            remote      <- conn.remoteAddress.map(_.asInstanceOf[InetSocketAddress])
            peerOpt <- conn.read(timeout = Some(config.handshakeTimeout)).map {
              case Some(status: Status) => Some(HandshakedPeer(remote, PeerInfo(status, 0)))
              case _                    => None
            }
            _ <- peerOpt match {
              case None =>
                log.info(s"${remote} handshake failed, close")
                conn.close
              case Some(peer) =>
                log.info(s"${remote} handshake succeed")
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
              case Some(peer) => transport.write(peer.remote, msg)
              case None       => F.unit
            }
          } yield ()

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
      }
}
