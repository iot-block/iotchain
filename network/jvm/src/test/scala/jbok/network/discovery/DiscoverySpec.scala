package jbok.network.discovery

import java.net.{InetAddress, InetSocketAddress}

import cats.effect.IO
import fs2._
import jbok.JbokSpec
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.codecs._
import jbok.common.testkit.ByteGen
import jbok.crypto.signature.ecdsa.SecP256k1
import jbok.network.execution._
import org.scalacheck.Arbitrary
import scodec.bits.ByteVector

import scala.concurrent.duration._

trait DiscoveryFixture {
  val config1 = DiscoveryConfig(
    "localhost",
    10000,
    Set.empty,
    100,
    100,
    10.seconds,
    10.seconds
  )
  val config2 = config1.copy(port = config1.port + 1)
  val keyPair1 = SecP256k1.generateKeyPair().unsafeRunSync()
  val keyPair2 = SecP256k1.generateKeyPair().unsafeRunSync()
  val discovery1 = Discovery[IO](config1, keyPair1)
  val discovery2 = Discovery[IO](config2, keyPair2)

  val from = Endpoint.makeEndpoint(new InetSocketAddress(config1.interface, config1.port), 0)
  val to = Endpoint.makeEndpoint(new InetSocketAddress(config2.interface, config2.port), 0)
  val ping = Ping(4, from, to, Long.MaxValue)
  val pong = Pong(from, Discovery.encodePacket[IO, Ping](ping, keyPair1).unsafeRunSync().mdc, Long.MaxValue)
  val neighboursCount = 9
  val neighborList = (1 to 9).map { n =>
    val newAddress: Array[Byte] = Array(31, 178, 1, n).map(_.toByte)
    val newId: Array[Byte] = Array.fill(64) { n.toByte }
    val socketAddress = new InetSocketAddress(InetAddress.getByAddress(newAddress), 0)
    val nodeId = Node(ByteVector(newId), socketAddress).id
    val neighbourEndpoint = Endpoint.makeEndpoint(socketAddress, 0)
    Neighbour(neighbourEndpoint, nodeId)
  }.toList

  val neighbors = Neighbours(neighborList, Long.MaxValue)
}

class DiscoverySpec extends JbokSpec {

  def roundtrip[A <: KadPacket](a: A)(implicit c: RlpCodec[A]) = {
    val bits = RlpCodec.encode(a).require
    RlpCodec.decode[A](bits).require.value shouldBe a
  }

  "Discovery" should {
    "codec messages" in new DiscoveryFixture {
      roundtrip(ping)
      roundtrip(pong)
      roundtrip(neighbors)
      forAll(ByteGen.genBytes(65), Arbitrary.arbitrary[Long].filter(_ >= 0)) {
        case (bytes, expiration) =>
          val findNode = FindNode(ByteVector(bytes), expiration)
          roundtrip(findNode)
      }
    }

    "codec packet" in new DiscoveryFixture {
      val packet = Discovery.encodePacket[IO, Ping](ping, keyPair1).unsafeRunSync()
      val decoded = Discovery.decodePacket[IO](packet.bytes).unsafeRunSync()
      val kadPacket = Discovery.extractMessage[IO](decoded).unsafeRunSync()

      decoded shouldBe packet
      kadPacket shouldBe ping
    }

    "respond Ping" in new DiscoveryFixture {
      val s1 = discovery1.flatMap(x => {
        val sleep = Sch.sleep[IO](1000.millis)
        val send = Stream.eval(x.sendMessage(new InetSocketAddress(config2.interface, config2.port), ping))
        val listen = x.receivedMessages()
        listen.concurrently(sleep ++ send)
      })

      val s2 = discovery2.flatMap(x => x.handleMessages())
      val (remote, packet, _) = s1.concurrently(s2).take(1).compile.toList.unsafeRunSync().head
      packet shouldBe a [Pong]
      packet.asInstanceOf[Pong].to shouldBe pong.to
      packet.asInstanceOf[Pong].pingHash shouldBe pong.pingHash
    }

    "respond FindNode" in new DiscoveryFixture {
      val findNode = FindNode(ByteVector.fill(65)(0.toByte), Long.MaxValue)
      val s1 = discovery1.flatMap(x => {
        val sleep = Sch.sleep[IO](1000.millis)
        val send = Stream.eval(x.sendMessage(new InetSocketAddress(config2.interface, config2.port), findNode))
        val listen = x.receivedMessages()
        listen.concurrently(sleep ++ send)
      })

      val s2 = discovery2.flatMap(x => x.handleMessages())
      val (remote, packet, _) = s1.concurrently(s2).take(1).compile.toList.unsafeRunSync().head
      packet shouldBe a[Neighbours]
    }

    "respond Neighbours" ignore new DiscoveryFixture {
      val s1 = discovery1.flatMap(x => {
        val sleep = Sch.sleep[IO](1000.millis)
        val send = Stream.eval(x.sendMessage(new InetSocketAddress(config2.interface, config2.port), neighbors))
        val listen = x.receivedMessages()
        listen.concurrently(sleep ++ send)
      })

      val s2 = discovery2.flatMap(x => x.handleMessages())
      val (remote, packet, _) = s1.concurrently(s2).take(1).compile.toList.unsafeRunSync().head
    }
  }
}
