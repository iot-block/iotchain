package jbok.core.peer

import cats.effect.IO
import cats.implicits._
import jbok.common.testkit._
import jbok.core.CoreSpec
import jbok.core.testkit._
import org.scalacheck.Gen

class PeerStoreSpec extends CoreSpec {
  "PeerStore" should {

    "put and get PeerUri" in check { objects =>
      val store = objects.get[PeerStore[IO]]
      val uris  = random[List[PeerUri]](Gen.listOfN(100, arbPeerUri.arbitrary))
      for {
        _  <- uris.traverse(store.put)
        xs <- store.getAll
        _ = xs should contain theSameElementsAs uris.toSet
      } yield ()
    }
  }
}
