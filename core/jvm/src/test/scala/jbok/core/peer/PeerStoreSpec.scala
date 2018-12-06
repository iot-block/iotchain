package jbok.core.peer

import cats.effect.IO
import jbok.JbokSpec
import jbok.common.execution._
import jbok.common.testkit._
import jbok.core.testkit._
import jbok.persistent.KeyValueDB
import jbok.persistent.testkit._
import org.scalacheck.Gen
import cats.implicits._

class PeerStoreSpec extends JbokSpec {
  "PeerStore" should {
    val db = random[KeyValueDB[IO]]
    val store = PeerStorePlatform.fromKV[IO](db)

    "put and get PeerNode" in {
      val nodes = random[List[PeerNode]](Gen.listOfN(100, arbPeerNode.arbitrary))
      nodes.traverse(store.putNode).unsafeRunSync()
      nodes.map { node =>
        store.getNodeOpt(node.id).unsafeRunSync() shouldBe Some(node)
      }

      val xs = store.getNodes.unsafeRunSync()
      xs should contain theSameElementsAs nodes
    }
  }
}
