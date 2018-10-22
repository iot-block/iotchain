//package jbok.core.peer.discovery
//
//import java.net.{InetAddress, InetSocketAddress}
//
//import cats.effect.IO
//import fs2._
//import jbok.JbokSpec
//import jbok.codec.rlp.RlpCodec
//import jbok.core.NodeStatus
//import jbok.core.peer.PeerNode
//import jbok.crypto.signature.{ECDSA, Signature}
//import jbok.network.execution._
//import scodec.bits.ByteVector
//import DiscoveryManager._
//
//class DiscoveryManagerSpec extends JbokSpec {
//  val config           = DiscoveryConfig()
//
//  val kp1   = Signature[ECDSA].generateKeyPair().unsafeRunSync()
//  val kp2   = Signature[ECDSA].generateKeyPair().unsafeRunSync()
//  val addr1 = new InetSocketAddress("localhost", 10000)
//  val addr2 = new InetSocketAddress("localhost", 10001)
//
//  val status1           = NodeStatus[IO].unsafeRunSync()
//  val status2           = NodeStatus[IO].unsafeRunSync()
//  status1.keyPair.setSync(Some(kp1)).unsafeRunSync()
//  status2.keyPair.setSync(Some(kp2)).unsafeRunSync()
//  val discoveryManager = DiscoveryManager[IO](config, status1).unsafeRunSync()
//
//  val pipe = discoveryManager.pipe
//
//  val from            = Endpoint.makeEndpoint(addr1, 20000)
//  val to              = Endpoint.makeEndpoint(addr2, 20001)
//  val ping            = Ping(from, to)
//  val pong            = Pong(from, DiscoveryManager.encodePacket[IO](ping, kp1).unsafeRunSync().mdc)
//  val neighboursCount = 9
//  val neighborList = (1 to 9).map { n =>
//    val newAddress: Array[Byte] = Array(31, 178, 1, n).map(_.toByte)
//    val newId: Array[Byte]      = Array.fill(64) { n.toByte }
//    val socketAddress           = new InetSocketAddress(InetAddress.getByAddress(newAddress), 0)
//    val nodeId                  = PeerNode(ByteVector(newId), socketAddress).id
//    val neighbourEndpoint       = Endpoint.makeEndpoint(socketAddress, 0)
//    Neighbour(neighbourEndpoint, nodeId)
//  }.toList
//
//  val neighbors = Neighbours(neighborList)
//
//  def roundtrip(a: KadPacket)(implicit c: RlpCodec[KadPacket]) = {
//    val bits = RlpCodec.encode(a).require
//    RlpCodec.decode[KadPacket](bits).require.value shouldBe a
//  }
//
//  "DiscoveryManager" should {
//    "roundtrip KAD messages" in {
//      roundtrip(ping)
//      roundtrip(pong)
//      roundtrip(neighbors)
//    }
//
//    "serve pipe" in {
//      val pingUdp = DiscoveryManager.encodePacket[IO](ping, kp1).unsafeRunSync()
//      val (_, udp1) =
//        runLog(Stream(addr1 -> pingUdp).covary[IO].through(discoveryManager.pipe)).head
//
//      udp1.kadPacket[IO].unsafeRunSync() shouldBe pong
//
//      val pongUdp = DiscoveryManager.encodePacket[IO](pong, kp2).unsafeRunSync()
//
//      val (_, udp2) =
//        runLog(Stream(addr2 -> pongUdp).covary[IO].through(discoveryManager.pipe)).head
//
//      val findNode = udp2.kadPacket[IO].unsafeRunSync()
//      val findNodeUdp = DiscoveryManager.encodePacket[IO](findNode, kp1).unsafeRunSync()
//
//      val (_, udp3)= runLog(Stream(addr1 -> findNodeUdp).covary[IO].through(pipe)).head
//      val neighbours = udp3.kadPacket[IO].unsafeRunSync()
//
//      val neighboursUdp = encodePacket[IO](neighbours, kp2).unsafeRunSync()
//      val udpList = runLog(Stream(addr2 -> neighboursUdp).covary[IO].through(pipe)).map(_._2)
//      println(udpList)
//    }
//  }
//}
