package jbok.core.peer.discovery

import cats.effect.IO
import cats.implicits._
import jbok.JbokSpec
import jbok.common.testkit._
import jbok.core.peer.PeerNode
import jbok.core.testkit._
import jbok.crypto._
import jbok.crypto.signature.KeyPair
import jbok.crypto.testkit._
import org.scalacheck.Gen
import scodec.bits._

class PeerTableSpec extends JbokSpec {
  "PeerTable" should {
    val N = 10
    val nodes = random[List[PeerNode]](Gen.listOfN(N, arbPeerNode.arbitrary))

    "calc log dist" in {
      val a = BitVector.fromValidBin("1" * 32 * 8)
      val b = BitVector.fromValidBin("1" + "0" * (32 * 8 - 1))

      val dist = PeerTable.logDist(a.bytes, b.bytes)
      dist shouldBe 255
      val i = if (dist <= PeerTable.bucketMinDistance) 0 else dist - PeerTable.bucketMinDistance - 1
      i shouldBe 15
    }

    "ord bytes by dist to target" in {
      val t = BitVector.fromValidBin("0" * 8)
      val a = BitVector.fromValidBin("0" * 4 + "0001")
      val b = BitVector.fromValidBin("0" * 4 + "0010")
      val ord = PeerTable.ordByTargetDist(t.bytes)
      ord.compare(a.bytes, b.bytes) shouldBe -1
    }

    "add nodes" in {
      val table = random[PeerTable[IO]]
      nodes.traverse(table.addNode).unsafeRunSync()

      table.getBuckets.unsafeRunSync().map(_.entries.length).sum shouldBe N
      table.getBuckets.unsafeRunSync().map(_.replacements.length).sum shouldBe 0
    }

    "get closest nodes w.r.t target" in {
      val table = random[PeerTable[IO]]
      nodes.traverse(table.addNode).unsafeRunSync()

      val target = random[KeyPair].public
      val targetHash = target.bytes.kec256

      val result = table.closest(targetHash, 1).unsafeRunSync()
    }
  }
}
