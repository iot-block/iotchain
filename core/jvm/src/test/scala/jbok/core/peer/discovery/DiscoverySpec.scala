package jbok.network.discovery

import java.util.UUID

import cats.effect.IO
import fs2._
import jbok.JbokSpec
import jbok.common.execution._
import jbok.common.testkit._
import jbok.core.peer.PeerNode
import jbok.core.peer.discovery._
import jbok.core.testkit._
import jbok.crypto.signature.KeyPair
import jbok.crypto.testkit._
import org.scalacheck.Gen
import scodec.Codec
import cats.implicits._
import jbok.core.peer.discovery.KadPacket._

import scala.concurrent.duration._

class DiscoverySpec extends JbokSpec {
  "Discovery" should {
    "roundtrip kad packets" in {
      def roundtrip[A <: KadPacket](a: A) = {
        val bits = Codec.encode[KadPacket](a).require
        Codec.decode[KadPacket](bits).require.value shouldBe a
      }

      val ping      = Ping(random[PeerNode], Long.MaxValue, UUID.randomUUID())
      val pong      = Pong(random[PeerNode], Long.MaxValue, UUID.randomUUID())
      val findNode  = FindNode(random[PeerNode], random[KeyPair].public, Long.MaxValue, UUID.randomUUID())
      val nodes     = random[List[PeerNode]](Gen.listOfN(16, arbPeerNode.arbitrary))
      val neighbors = Neighbours(random[PeerNode], nodes, Long.MaxValue, UUID.randomUUID())

      roundtrip(ping)
      roundtrip(pong)
      roundtrip(findNode)
      roundtrip(neighbors)
    }

    "handle Ping" in {
      val d1 = random[Discovery[IO]](genDiscovery(10001))
      val d2 = random[Discovery[IO]](genDiscovery(10002))
      val p = for {
        fiber <- Stream(d1, d2).map(_.serve).parJoinUnbounded.compile.drain.start
        _     <- T.sleep(1.second)
        pong  <- d1.ping(d2.peerNode)
        _     <- fiber.cancel
      } yield ()

      p.unsafeRunSync()
    }

    "handle FindNode" in {
      val d1    = random[Discovery[IO]](genDiscovery(10001))
      val d2    = random[Discovery[IO]](genDiscovery(10002))
      val N     = 10
      val nodes = random[List[PeerNode]](Gen.listOfN(N, arbPeerNode.arbitrary))

      val p = for {
        _     <- nodes.traverse(d2.table.addNode)
        fiber <- Stream(d1, d2).map(_.serve).parJoinUnbounded.compile.drain.start
        _     <- T.sleep(1.second)
        _ = d1.table.getBuckets.unsafeRunSync().flatMap(_.entries).length shouldBe 0
        _ <- d1.findNode(d2.peerNode, d2.peerNode.pk)
        _ <- d1.findNode(d2.peerNode, d2.peerNode.pk)
        _ = d1.table.getBuckets.unsafeRunSync().flatMap(_.entries).length shouldBe N
        _ <- fiber.cancel
      } yield ()

      p.unsafeRunSync()
    }
  }
}
