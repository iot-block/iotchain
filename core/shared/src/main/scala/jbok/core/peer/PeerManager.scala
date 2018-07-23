package jbok.core.peer

import java.net.InetSocketAddress

import cats.effect.ConcurrentEffect
import cats.implicits._
import fs2._
import fs2.async.mutable.{Signal, Topic}
import fs2.async.{Promise, Ref}
import fs2.io.tcp.Socket
import jbok.core.BlockChain
import jbok.core.configs.PeerManagerConfig
import jbok.core.messages.{Message, Messages, Status}
import jbok.network.NetAddress
import jbok.network.execution._
import scodec.bits.ByteVector

case class PeerManager[F[_]](
    config: PeerManagerConfig,
    stopWhenTrue: Signal[F, Boolean],
    pending: Ref[F, Map[PeerId, Peer[F]]],
    handshaked: Ref[F, Map[PeerId, HandshakedPeer[F]]],
    events: Topic[F, Option[PeerEvent]],
    blockchain: BlockChain[F]
)(implicit F: ConcurrentEffect[F]) {

  private[this] val log = org.log4s.getLogger

  private val decode: Pipe[F, Byte, Message] =
    _.chunks.map(chunks => Messages.decode(ByteVector(chunks.toArray)).require)

  private val encode: Pipe[F, Message, Byte] =
    _.flatMap[Byte](m => Stream.chunk(Chunk.array(Messages.encode(m).toArray)).covary[F])

  private def fuse(socket: Socket[F], incoming: Boolean, p: Promise[F, Unit]): Stream[F, Unit] =
    for {
      conn <- Stream.eval(Connection[F](socket.close))
      remote <- Stream.eval(socket.remoteAddress)
      local <- Stream.eval(socket.localAddress)

      _ = if (incoming) {
        log.info(s"accept a new incoming connection from ${remote} to ${local}")
      } else {
        log.info(s"create a new outgoing connection form ${local} to ${remote}")
      }

      peer = Peer(remote, incoming = false, getStatus, conn)
      _ <- Stream.eval(pending.modify(_ + (peer.id -> peer)))
      _ <- socket
        .reads(256 * 1024)
        .through(decode)
        .onFinalize(socket.endOfInput)
        .evalMap {
          case msg: Status => conn.inbound.publish1(msg.some)
          case msg         => events.publish1(PeerEvent.PeerRecv(PeerId(remote.toString), msg).some)
        }
        .merge(
          conn.outbound.dequeue
            .through(encode)
            .to(socket.writes())
            .onFinalize(socket.endOfOutput)
        )
        .concurrently(Stream.eval(handshake(peer) *> p.complete(())))
        .onFinalize(socket.close *> onPeerDrop(PeerId(remote.toString)))
        .handleErrorWith(e => Stream.eval(F.delay(e.printStackTrace())))

    } yield ()

  private def onPeerDrop(peerId: PeerId): F[Unit] =
    pending.modify(_ - peerId) *>
      handshaked.modify(_ - peerId) *>
      events.publish1(PeerEvent.PeerDrop(peerId).some) *>
      F.delay(log.info(s"${peerId} dropped"))

  private def handshake(peer: Peer[F]): F[Unit] =
    for {
      hp <- peer.handshake
      _ <- pending.modify(_ - peer.id)
      _ <- handshaked.modify(_ + (hp.id -> hp))
      _ <- events.publish1(PeerEvent.PeerAdd(hp.id).some)
      _ = log.info(s"peer ${peer.id} handshaked")
    } yield ()

  def getPendingPeers: F[Map[PeerId, Peer[F]]] = pending.get

  def getHandshakedPeers: F[Map[PeerId, HandshakedPeer[F]]] = handshaked.get

  def getStatus: F[Status] =
    for {
      number <- blockchain.getBestBlockNumber
      headerOpt <- blockchain.getBlockHeaderByNumber(number)
      genesis <- blockchain.genesisHeader
      header = headerOpt.getOrElse(genesis)
    } yield Status(1, 1, header.hash, genesis.hash)

  def listen(addr: NetAddress = config.bindAddr, maxConcurrent: Int = config.maxIncomingPeers): F[Unit] = {
    val bind = new InetSocketAddress(addr.host, addr.port.get)

    fs2.async.promise[F, Unit].flatMap { p =>
      val stream = fs2.io.tcp
        .serverWithLocalAddress[F](bind, maxQueued = 0, reuseAddress = true, receiveBufferSize = 256 * 1024)
        .map {
          case Left(bindAddr) =>
            log.info(s"listening on ${bindAddr}")
            Stream.eval(p.complete(()))

          case Right(s) =>
            for {
              socket <- s
              p2 <- Stream.eval(fs2.async.promise[F, Unit])
              _ <- fuse(socket, incoming = true, p2)
            } yield ()
        }
        .join(maxConcurrent)
        .handleErrorWith(e => Stream.eval(F.delay(e.printStackTrace())))
        .interruptWhen(stopWhenTrue)

      stopWhenTrue.set(false) *> F.start(stream.compile.drain).void *> p.get
    }
  }

  def connect(addr: NetAddress): F[Unit] = {
    val to = new InetSocketAddress(addr.host, addr.port.get)

    fs2.async.promise[F, Unit].flatMap { p =>
      val stream = fs2.io.tcp
        .client[F](to, keepAlive = true, noDelay = true)
        .flatMap(socket => fuse(socket, incoming = false, p))
        .onFinalize(F.delay(log.info(s"connect down")))
        .interruptWhen(stopWhenTrue)

      stopWhenTrue.set(false) *> F.start(stream.compile.drain) *> p.get
    }
  }

  def disconnect(peerId: PeerId): F[Unit] =
    for {
      peers <- handshaked.get
      _ <- peers.get(peerId) match {
        case Some(peer) =>
          peer.conn.close *> handshaked.modify(_ - peerId) *> events.publish1(PeerEvent.PeerDrop(peerId).some)
        case None => F.unit
      }
    } yield ()

  def broadcast(msg: Message): F[Unit] =
    for {
      peers <- handshaked.get.map(_.values.toList)
      _ <- peers match {
        case Nil =>
          log.info(s"empty peers, ignore")
          F.unit
        case l =>
          log.info(s"broadcast message to ${l.length} peers")
          peers.traverse(_.send(msg))
      }
    } yield ()

  def sendMessage(peerId: PeerId, msg: Message): F[Unit] =
    for {
      peers <- handshaked.get
      _ <- peers.get(peerId) match {
        case Some(peer) => peer.send(msg)
        case None       => F.unit
      }
    } yield ()

  def subscribe() = events.subscribe(32).unNone

  def subscribeEvents(): Stream[F, PeerEvent] = events.subscribe(32).unNone.collect {
    case x: PeerEvent.PeerAdd  => x
    case x: PeerEvent.PeerDrop => x
  }

  def subscribeMessages(): Stream[F, PeerEvent.PeerRecv] = events.subscribe(32).unNone.collect {
    case x: PeerEvent.PeerRecv =>
      log.info(s"received message $x")
      x
  }

  def stop: F[Unit] = stopWhenTrue.set(true)
}

object PeerManager {
  def apply[F[_]: ConcurrentEffect](
      config: PeerManagerConfig,
      blockchain: BlockChain[F]
  ): F[PeerManager[F]] =
    for {
      stopWhenTrue <- fs2.async.signalOf[F, Boolean](true)
      pending <- fs2.async.refOf[F, Map[PeerId, Peer[F]]](Map.empty)
      handshaked <- fs2.async.refOf[F, Map[PeerId, HandshakedPeer[F]]](Map.empty)
      events <- fs2.async.topic[F, Option[PeerEvent]](None)
    } yield PeerManager(config, stopWhenTrue, pending, handshaked, events, blockchain)
}
