package jbok.core.peer

import cats.effect.IO
import cats.implicits._
import jbok.common.testkit._
import jbok.core.CoreSpec
import jbok.core.testkit._
import jbok.persistent.KeyValueDB
import org.scalacheck.Gen

class PeerStoreSpec extends CoreSpec {
  "PeerStore" should {
    val objects = locator.unsafeRunSync()
    val db      = objects.get[KeyValueDB[IO]]
    val store   = objects.get[PeerStore[IO]]

    "put and get PeerUri" in {
      val uris = random[List[PeerUri]](Gen.listOfN(100, arbPeerUri.arbitrary))
      uris.traverse(store.put).unsafeRunSync()
      uris.map { uri =>
        store.get(uri.uri.toString).unsafeRunSync() shouldBe uri
      }

      val xs = store.getAll.unsafeRunSync()
      xs should contain theSameElementsAs uris
    }
  }
}
