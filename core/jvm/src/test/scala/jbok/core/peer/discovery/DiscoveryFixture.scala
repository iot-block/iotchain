package jbok.network.discovery

import java.net.InetSocketAddress

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.config.Configs.DiscoveryConfig
import jbok.core.peer.discovery._
import jbok.crypto.signature.{ECDSA, Signature}
import jbok.common.execution._
import jbok.network.transport.UdpTransport
import jbok.persistent.KeyValueDB

class DiscoveryFixture(port: Int) {
  val keyPair            = Signature[ECDSA].generateKeyPair().unsafeRunSync()
  val addr               = new InetSocketAddress("localhost", port)
  val db                 = KeyValueDB.inMemory[IO].unsafeRunSync()
  val transport = UdpTransport[IO](addr)
  val config = DiscoveryConfig().copy(port = port)
  val discovery = Discovery[IO](config, keyPair, transport, db).unsafeRunSync()
  val table = discovery.table
  val selfNode = discovery.table.selfNode
}

class DiscoverySpec extends JbokSpec {
  val fix1 = new DiscoveryFixture(10001)
  val fix2 = new DiscoveryFixture(10002)

  val fiber1 = fix1.discovery.serve.compile.drain.start.unsafeRunSync()
  val fiber2 = fix2.discovery.serve.compile.drain.start.unsafeRunSync()
  Thread.sleep(1000)

  "Discovery" should {
    "handle Ping" in {
      val pong      = fix1.discovery.ping(fix2.selfNode.id, fix2.selfNode.addr).unsafeRunSync()
      val neighbors = fix1.discovery.findNode(fix2.selfNode, fix2.selfNode.pk).unsafeRunSync()
      neighbors.length shouldBe 0
    }
  }

  override protected def afterAll(): Unit = {
    fiber1.cancel.unsafeRunSync()
    fiber2.cancel.unsafeRunSync()
  }
}
//
//trait DiscoveryServerFixture {
//  val config1 = DiscoveryConfig(
//    "localhost",
//    10000,
//    Set.empty,
//    100,
//    100,
//    10.seconds,
//    10.seconds
//  )
//  val config2 = config1.copy(port = config1.port + 1)
//  val keyPair1 = SecP256k1.generateKeyPair().unsafeRunSync()
//  val keyPair2 = SecP256k1.generateKeyPair().unsafeRunSync()
//  val discovery1 = DiscoveryServer[IO](config1, keyPair1)
//  val discovery2 = DiscoveryServer[IO](config2, keyPair2)
//
//  val from = Endpoint.makeEndpoint(new InetSocketAddress(config1.interface, config1.port), 0)
//  val to = Endpoint.makeEndpoint(new InetSocketAddress(config2.interface, config2.port), 0)
//  val ping = Ping(4, from, to, Long.MaxValue)
//  val pong = Pong(from, DiscoveryServer.encodePacket[IO, Ping](ping, keyPair1).unsafeRunSync().mdc, Long.MaxValue)
//  val neighboursCount = 9
//  val neighborList = (1 to 9).map { n =>
//    val newAddress: Array[Byte] = Array(31, 178, 1, n).map(_.toByte)
//    val newId: Array[Byte] = Array.fill(64) { n.toByte }
//    val socketAddress = new InetSocketAddress(InetAddress.getByAddress(newAddress), 0)
//    val nodeId = Node(ByteVector(newId), socketAddress).id
//    val neighbourEndpoint = Endpoint.makeEndpoint(socketAddress, 0)
//    Neighbour(neighbourEndpoint, nodeId)
//  }.toList
//
//  val neighbors = Neighbours(neighborList, Long.MaxValue)
//}
//
//class DiscoverySpec extends JbokSpec {
//
//  def roundtrip[A <: KadPacket](a: A)(implicit c: RlpCodec[A]) = {
//    val bits = RlpCodec.encode(a).require
//    RlpCodec.decode[A](bits).require.value shouldBe a
//  }
//
//  "Discovery" should {
//    "codec messages" in new DiscoveryServerFixture {
//      roundtrip(ping)
//      roundtrip(pong)
//      roundtrip(neighbors)
//      forAll(ByteGen.genBytes(65), Arbitrary.arbitrary[Long].filter(_ >= 0)) {
//        case (bytes, expiration) =>
//          val findNode = FindNode(ByteVector(bytes), expiration)
//          roundtrip(findNode)
//      }
//    }
//
//    "codec packet" in new DiscoveryServerFixture {
//      val packet = DiscoveryServer.encodePacket[IO, Ping](ping, keyPair1).unsafeRunSync()
//      val decoded = DiscoveryServer.decodePacket[IO](packet.bytes).unsafeRunSync()
//      val kadPacket = DiscoveryServer.extractMessage[IO](decoded).unsafeRunSync()
//
//      decoded shouldBe packet
//      kadPacket shouldBe ping
//    }
//
//    "respond Ping" in new DiscoveryServerFixture {
//      val s1 = discovery1.flatMap(x => {
//        val sleep = Sch.sleep[IO](1000.millis)
//        val send = Stream.eval(x.sendMessage(new InetSocketAddress(config2.interface, config2.port), ping))
//        val listen = x.receivedMessages()
//        listen.concurrently(sleep ++ send)
//      })
//
//      val s2 = discovery2.flatMap(x => x.handleMessages())
//      val (remote, packet, _) = s1.concurrently(s2).take(1).compile.toList.unsafeRunSync().head
//      packet shouldBe a [Pong]
//      packet.asInstanceOf[Pong].to shouldBe pong.to
//      packet.asInstanceOf[Pong].pingHash shouldBe pong.pingHash
//    }
//
//    "respond FindNode" in new DiscoveryServerFixture {
//      val findNode = FindNode(ByteVector.fill(65)(0.toByte), Long.MaxValue)
//      val s1 = discovery1.flatMap(x => {
//        val sleep = Sch.sleep[IO](1000.millis)
//        val send = Stream.eval(x.sendMessage(new InetSocketAddress(config2.interface, config2.port), findNode))
//        val listen = x.receivedMessages()
//        listen.concurrently(sleep ++ send)
//      })
//
//      val s2 = discovery2.flatMap(x => x.handleMessages())
//      val (remote, packet, _) = s1.concurrently(s2).take(1).compile.toList.unsafeRunSync().head
//      packet shouldBe a[Neighbours]
//    }
//
//    "respond Neighbours" ignore new DiscoveryServerFixture {
//      val s1 = discovery1.flatMap(x => {
//        val sleep = Sch.sleep[IO](1000.millis)
//        val send = Stream.eval(x.sendMessage(new InetSocketAddress(config2.interface, config2.port), neighbors))
//        val listen = x.receivedMessages()
//        listen.concurrently(sleep ++ send)
//      })
//
//      val s2 = discovery2.flatMap(x => x.handleMessages())
//      val (remote, packet, _) = s1.concurrently(s2).take(1).compile.toList.unsafeRunSync().head
//    }
//  }
//}
