//package jbok.core.peer.discovery
//import java.net.{InetSocketAddress, URI}
//import java.time.Clock
//
//import cats.effect.{ConcurrentEffect, Sync}
//import cats.implicits._
//import fs2.async.Ref
//import fs2.{Scheduler, _}
//import jbok.codec.rlp.RlpCodec
//import jbok.codec.rlp.codecs._
//import jbok.core.NodeStatus
//import jbok.core.peer.PeerNode
//import jbok.core.peer.discovery.DiscoveryManager._
//import jbok.crypto._
//import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
//import jbok.network.transport.UdpTransport
//import org.bouncycastle.util.BigIntegers
//import scodec.bits.ByteVector
//
//import scala.concurrent.ExecutionContext
//
//case class DiscoveryManager[F[_]](
//    config: DiscoveryConfig,
//    nodeStatus: NodeStatus[F],
//    transport: UdpTransport[F, UdpPacket],
//    discovered: Ref[F, Map[ByteVector, (PeerNode, Long)]]
//)(implicit F: ConcurrentEffect[F], S: Scheduler, EC: ExecutionContext) {
//  private[this] val log = org.log4s.getLogger
//
//  val pipe: Pipe[F, (InetSocketAddress, UdpPacket), (InetSocketAddress, UdpPacket)] = { input =>
//    val output = input
//      .evalMap {
//        case (remote, udp) =>
//          extractMessage[F](udp).map(kad => (remote, kad, udp))
//      }
//      .evalMap[List[(InetSocketAddress, KadPacket)]] {
//        case (remote, ping: Ping, packet) =>
//          // remote port may be different to ping.from.udpPort
//          val to   = Endpoint.makeEndpoint(remote, ping.from.tcpPort)
//          val pong = Pong(to, packet.mdc)
//          log.info(s"received ${ping} from ${remote}, respond with ${pong}")
//          F.pure(List(remote -> pong))
//
//        case (remote, pong: Pong, packet) =>
//          log.info(s"received ${pong} from ${remote}")
//          val newNode = PeerNode(packet.nodeId, remote)
//          log.info(s"discovered new node ${newNode}")
//          for {
//            nodes      <- discovered.get
//            selfNodeId <- nodeStatus.keyPair.get.map(_.get.public.bytes)
//            result <- if (nodes.size < config.nodesLimit) {
//              discovered.modify(_ + (newNode.id -> (newNode, System.currentTimeMillis()))) *>
//                List(remote -> FindNode(selfNodeId)).pure[F]
//            } else {
//              val (earliestNode, _) = nodes.minBy { case (_, (_, timestamp)) => timestamp }
//              discovered.modify(_ - earliestNode + (newNode.id -> (newNode, System.currentTimeMillis()))).void *>
//                Nil.pure[F]
//            }
//          } yield result
//
//        case (remote, findNode: FindNode, _) =>
//          val neighbours = Neighbours(Nil)
//          log.info(s"received ${findNode}, respond with ${neighbours.nodes.length} neighbours")
//          F.pure(List(remote -> neighbours))
//
//        case (_, neighbours: Neighbours, _) =>
//          for {
//            nodes <- discovered.get
//          } yield {
//            val toPings = neighbours.nodes
//              .filterNot(n => nodes.contains(n.nodeId)) // filter out known nodes
//              .take(config.nodesLimit - nodes.size)
//
//            log.info(s"received ${neighbours.nodes.length} neighbours, left ${toPings.length} to ping")
//
//            toPings
//              .flatMap(x => Endpoint.toUdpAddress(x.endpoint).map(addr => addr -> x.endpoint))
//              .map {
//                case (toAddr, toEndpoint) =>
//                  val selfAddr: InetSocketAddress = new InetSocketAddress(config.interface, config.port)
//                  val tcpPort: Int                = 0
//                  val from                        = Endpoint.makeEndpoint(selfAddr, tcpPort)
//                  toAddr -> Ping(from, toEndpoint)
//              }
//          }
//      }
//
//    output
//      .flatMap(xs => Stream(xs: _*).covary[F])
//      .evalMap {
//        case (remote, kad) =>
//          for {
//            keyPair <- nodeStatus.keyPair.get.map(_.get)
//            udp     <- encodePacket[F](kad, keyPair)
//          } yield {
//            remote -> udp
//          }
//      }
//  }
//
//  def stream: Stream[F, Unit] =
//    transport
//      .serve(new InetSocketAddress("localhost", config.port), pipe)
//      .concurrently(S.awakeEvery[F](config.scanInterval).evalMap(_ => ping))
//
//  def ping: F[Unit] =
//    for {
//      nodes <- discovered.get.map(_.values.toList)
//      _ <- nodes.traverse {
//        case (node, _) =>
//          sendPing(Endpoint.makeEndpoint(node.addr, node.addr.getPort), node.addr)
//      }
//    } yield ()
//
//  private def sendPing(toEndpoint: Endpoint, toAddr: InetSocketAddress): F[Unit] =
//    for {
//      tcpPort <- nodeStatus.peerServer.get.map {
//        case Some(addr) => addr.getPort
//        case None       => 0
//      }
//      keyPair <- nodeStatus.keyPair.get.map(_.get)
//      _ <- nodeStatus.discoveryServer.get.flatMap {
//        case Some(addr) =>
//          val from = Endpoint.makeEndpoint(addr, tcpPort)
//          encodePacket[F](Ping(from, toEndpoint), keyPair).flatMap(udp =>
//            transport.send(toAddr, udp))
//        case None =>
//          log.warn("peer discovery server not running. Not sending ping message.")
//          F.unit
//      }
//    } yield ()
//
//  private def uriToSocketAddr(uri: URI): InetSocketAddress =
//    new InetSocketAddress(uri.getHost, uri.getPort)
//
//  private val clock = Clock.systemUTC()
//
//  private def expirationTimestamp = clock.instant().plusSeconds(config.messageExpiration.toSeconds).getEpochSecond
//}
//
//object DiscoveryManager {
//  def apply[F[_]](config: DiscoveryConfig, nodeStatus: NodeStatus[F])(implicit F: ConcurrentEffect[F],
//                                                                      S: Scheduler,
//                                                                      EC: ExecutionContext): F[DiscoveryManager[F]] = {
//    implicit val codec = RlpCodec[UdpPacket].codec
//    for {
//      discovered <- fs2.async.refOf[F, Map[ByteVector, (PeerNode, Long)]](Map.empty)
//      transport = UdpTransport[F, UdpPacket]()
//    } yield DiscoveryManager[F](config, nodeStatus, transport, discovered)
//  }
//
//  private[jbok] def encodePacket[F[_]](msg: KadPacket, keyPair: KeyPair)(implicit F: Sync[F]): F[UdpPacket] =
//    F.delay {
//      val payload   = RlpCodec.encode(msg).require.bytes
//      val hash      = payload.kec256
//      val signature = Signature[ECDSA].sign(hash.toArray, keyPair, None).unsafeRunSync()
//
//      val sigBytes =
//        BigIntegers.asUnsignedByteArray(32, signature.r) ++
//          BigIntegers.asUnsignedByteArray(32, signature.s) ++
//          Array[Byte]((signature.v - 27).toByte)
//
//      val forSha = sigBytes ++ payload.toArray
//      val mdc    = forSha.kec256
//
//      UdpPacket(ByteVector(mdc ++ forSha))
//    }
//
//  private[jbok] def decodePacket[F[_]](bytes: ByteVector)(implicit F: Sync[F]): F[UdpPacket] =
//    if (bytes.length < 98) {
//      F.raiseError[UdpPacket](new RuntimeException("bad message"))
//    } else {
//      val packet   = UdpPacket(bytes)
//      val mdcCheck = bytes.drop(32).kec256
//      if (packet.mdc == mdcCheck) {
//        F.pure(packet)
//      } else {
//        F.raiseError[UdpPacket](new RuntimeException("mdc check failed"))
//      }
//    }
//
//  private[jbok] def extractMessage[F[_]](packet: UdpPacket)(implicit F: Sync[F]): F[KadPacket] = F.delay {
//    RlpCodec.decode[KadPacket](packet.data.bits).require.value
//  }
//}
