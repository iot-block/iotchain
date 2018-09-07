package jbok.network.discovery

import java.net.InetSocketAddress
import java.time.Clock

import cats.effect.{Effect, IO, Sync}
import cats.implicits._
import fs2._
import fs2.async.Ref
import fs2.io.udp.{AsynchronousSocketGroup, Packet}
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.codecs._
import jbok.crypto._
import jbok.crypto.signature.KeyPair
import jbok.crypto.signature.ecdsa.SecP256k1
import org.bouncycastle.util.BigIntegers
import scodec.bits.ByteVector

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

abstract class Discovery[F[_]](
    config: DiscoveryConfig,
    keyPair: KeyPair,
    clock: Clock,
    nodeInfo: Ref[F, Map[ByteVector, (Node, Long)]]
)(implicit F: Effect[F]) {
  val selfNodeId: ByteVector = keyPair.public.bytes

  def stop: F[Unit]

  def sendMessage[A <: KadPacket](remote: InetSocketAddress, msg: A)(implicit c: RlpCodec[A]): F[Unit]

  def receivedMessages(timeout: Option[FiniteDuration] = None): Stream[F, (InetSocketAddress, KadPacket, UdpPacket)]

  def handleMessages(): Stream[F, Unit] = receivedMessages().evalMap {
    case (remote, ping: Ping, packet) =>
      val to = Endpoint.makeEndpoint(remote, ping.from.tcpPort)
      sendMessage(remote, Pong(to, packet.mdc, expirationTimestamp))

    case (remote, pong: Pong, packet) =>
      val newNode = Node(packet.nodeId, remote)
      for {
        nodes <- nodeInfo.get
        _ <- if (nodes.size < config.nodesLimit) {
          nodeInfo.modify(_ + (newNode.id -> (newNode, System.currentTimeMillis()))) *>
            sendMessage(remote, FindNode(selfNodeId, expirationTimestamp))
        } else {
          val (earliestNode, _) = nodes.minBy { case (_, (_, timestamp)) => timestamp }
          nodeInfo.modify(_ - earliestNode + (newNode.id -> (newNode, System.currentTimeMillis()))).void
        }
      } yield ()

    case (remote, findNode: FindNode, packet) =>
      sendMessage(remote, Neighbours(Nil, expirationTimestamp))

    case (remote, neighbours: Neighbours, packet) =>
      for {
        nodes <- nodeInfo.get
        toPings = neighbours.nodes
          .filterNot(n => nodes.contains(n.nodeId)) // not already on the list
          .take(config.nodesLimit - nodes.size)
        _ <- toPings.traverse(n => Endpoint.toUdpAddress(n.endpoint).fold(F.unit)(addr => sendPing(n.endpoint, addr)))
      } yield ()

  }

  private[jbok] def sendPing(toEndpoint: Endpoint, toAddr: InetSocketAddress): F[Unit] = {
    val selfAddr: InetSocketAddress = new InetSocketAddress(config.interface, config.port)
    val tcpPort: Int                = 0
    val from                        = Endpoint.makeEndpoint(selfAddr, tcpPort)
    sendMessage(toAddr, Ping(4, from, toEndpoint, expirationTimestamp))
  }

  private[jbok] val expirationTimeSec = config.messageExpiration.toSeconds

  private[jbok] def expirationTimestamp = clock.instant().plusSeconds(expirationTimeSec).getEpochSecond

}

object Discovery {
  def apply[F[_]](
      config: DiscoveryConfig,
      keyPair: KeyPair
  )(implicit F: Effect[F], AG: AsynchronousSocketGroup, EC: ExecutionContext): Stream[F, Discovery[F]] =
    for {
      nodeInfo <- Stream.eval(fs2.async.refOf[F, Map[ByteVector, (Node, Long)]](Map.empty))
      socket   <- fs2.io.udp.open[F](new InetSocketAddress(config.interface, config.port), reuseAddress = true)
    } yield
      new Discovery[F](config, keyPair, Clock.systemDefaultZone(), nodeInfo) {
        override def stop: F[Unit] =
          socket.close

        override def sendMessage[A <: KadPacket](remote: InetSocketAddress, msg: A)(implicit c: RlpCodec[A]): F[Unit] =
          for {
            p <- encodePacket[F, A](msg, keyPair)
            _ <- socket.write(Packet(remote, Chunk.array(p.bytes.toArray)))
          } yield ()

        override def receivedMessages(
            timeout: Option[FiniteDuration]): Stream[F, (InetSocketAddress, KadPacket, UdpPacket)] =
          socket
            .reads(timeout)
            .evalMap(packet => {
              for {
                udpPacket <- decodePacket(ByteVector(packet.bytes.toArray))
                message   <- extractMessage(udpPacket)
              } yield (packet.remote, message, udpPacket)
            })
      }

  private[jbok] def encodePacket[F[_], A <: KadPacket](msg: A, keyPair: KeyPair)(implicit F: Sync[F],
                                                                                 C: RlpCodec[A]): F[UdpPacket] =
    F.delay {
      val encodedData = RlpCodec.encode(msg).require.bytes
      val payload     = msg.packetType +: encodedData
      val toSign      = payload.kec256
      val signature   = SecP256k1.sign(toSign.toArray, keyPair, None).unsafeRunSync()

      val sigBytes =
        BigIntegers.asUnsignedByteArray(32, signature.r) ++
          BigIntegers.asUnsignedByteArray(32, signature.s) ++
          Array[Byte]((signature.v - 27).toByte)

      val forSha = sigBytes ++ Array(msg.packetType) ++ encodedData.toArray
      val mdc    = forSha.kec256

      UdpPacket(ByteVector(mdc ++ sigBytes ++ Array(msg.packetType) ++ encodedData.toArray))
    }

  private[jbok] def decodePacket[F[_]](bytes: ByteVector)(implicit F: Sync[F]): F[UdpPacket] =
    if (bytes.length < 98) {
      F.raiseError[UdpPacket](new RuntimeException("bad message"))
    } else {
      val packet   = UdpPacket(bytes)
      val mdcCheck = bytes.drop(32).kec256
      if (packet.mdc == mdcCheck) {
        F.pure(packet)
      } else {
        F.raiseError[UdpPacket](new RuntimeException("mdc check failed"))
      }
    }

  private[jbok] def extractMessage[F[_]](packet: UdpPacket)(implicit F: Sync[F]): F[KadPacket] = F.delay {
    packet.packetType match {
      case Ping.packetType       => RlpCodec.decode[Ping](packet.data.bits).require.value
      case Pong.packetType       => RlpCodec.decode[Pong](packet.data.bits).require.value
      case FindNode.packetType   => RlpCodec.decode[FindNode](packet.data.bits).require.value
      case Neighbours.packetType => RlpCodec.decode[Neighbours](packet.data.bits).require.value
      case _                     => throw new RuntimeException(s"Unknown packet type ${packet.packetType}")
    }
  }
}
