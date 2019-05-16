package jbok.app.service

import cats.effect.IO
import jbok.app.AppSpec
import jbok.core.peer.{PeerManager, PeerUri}
import jbok.core.api.AdminAPI
import jbok.common.testkit._
import jbok.core.config.CoreConfig
import jbok.core.testkit._

class AdminAPISpec extends AppSpec {
  "AdminAPI" should {
    val objects = locator.unsafeRunSync()
    val admin = objects.get[AdminAPI[IO]]

    "addPeer" in {
      val peerManager = objects.get[PeerManager[IO]]
      val uri = random[PeerUri]
      admin.addPeer(uri.uri.toString).unsafeRunSync()
      peerManager.outgoing.store.get(uri.uri.toString).unsafeRunSync()
    }

    "getCoreConfig" in {
      admin.getCoreConfig.unsafeRunSync() shouldBe objects.get[CoreConfig]
    }
  }
}
