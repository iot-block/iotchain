package jbok.network.discovery

import java.net.{InetAddress, InetSocketAddress}

import cats.effect.IO
import fs2._
import jbok.JbokSpec
import jbok.common.execution._
import jbok.common.testkit._
import jbok.core.peer.discovery._
import jbok.core.testkit._
import jbok.crypto.signature.KeyPair
import jbok.crypto.testkit._
import scodec.Codec

import scala.concurrent.duration._

class DiscoverySpec extends JbokSpec {
  "Discovery" should {
    val d1 = random[Discovery[IO]](genDiscovery(10001))
    val d2 = random[Discovery[IO]](genDiscovery(10002))

    def roundtrip[A <: KadPacket](a: A) = {
      val bits = Codec.encode[KadPacket](a).require
      Codec.decode[KadPacket](bits).require.value shouldBe a
    }

    val from = d1.endpoint
    val to   = d2.endpoint
    val ping = Ping(4, from, to, Long.MaxValue)
    val pong = Pong(from, d1.encode(ping).unsafeRunSync().hash, Long.MaxValue)
    val N    = 9
    val neighborList = (1 to N).map { n =>
      val newAddress: Array[Byte] = Array(31, 178, 1, n).map(_.toByte)
      val newId: Array[Byte]      = Array.fill(64) { n.toByte }
      val socketAddress           = new InetSocketAddress(InetAddress.getByAddress(newAddress), 0)
      val nodeId                  = KeyPair.Public(newId)
      val neighbourEndpoint       = Endpoint.makeEndpoint(socketAddress, 0)
      Neighbour(neighbourEndpoint, nodeId)
    }.toList
    val neighbors = Neighbours(neighborList, Long.MaxValue)

    "roundtrip kad packets" in {
      roundtrip(ping)
      roundtrip(pong)
      roundtrip(neighbors)
      forAll { (keyPair: KeyPair, expiration: Long) =>
        val findNode = FindNode(keyPair.public, expiration.abs)
        roundtrip(findNode)
      }
    }

    "handle Ping" in {
      val p = for {
        fiber <- Stream(d1, d2).map(_.serve).parJoinUnbounded.compile.drain.start
        _     <- T.sleep(1.second)
        pong  <- d1.ping(d2.peerNode.id, d2.peerNode.addr)
        _ = pong.to shouldBe d1.endpoint
        _ <- fiber.cancel
      } yield ()

      p.unsafeRunSync()
    }

    "handle FindNode" in {
      val p = for {
        fiber <- Stream(d1, d2).map(_.serve).parJoinUnbounded.compile.drain.start
        _     <- T.sleep(1.second)
        nodes <- d1.findNode(d2.peerNode, d2.peerNode.pk)
        _ = println(nodes)
        _ <- fiber.cancel
      } yield ()

      p.unsafeRunSync()
    }
  }
}
