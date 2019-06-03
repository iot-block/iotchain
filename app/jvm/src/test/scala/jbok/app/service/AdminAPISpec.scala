package jbok.app.service

import cats.effect.IO
import jbok.app.AppSpec
import jbok.core.peer.{PeerManager, PeerUri}
import jbok.core.api.AdminAPI
import jbok.core.config.FullConfig

class AdminAPISpec extends AppSpec {
  "AdminAPI" should {
    "addPeer" in check { objects =>
      val admin       = objects.get[AdminAPI[IO]]
      val peerManager = objects.get[PeerManager[IO]]
      val uri         = random[PeerUri]
      for {
        _ <- admin.addPeer(uri.uri.toString)
        _ <- peerManager.outgoing.store.get(uri.uri.toString)
      } yield ()
    }

    "getConfig" in check { objects =>
      val admin = objects.get[AdminAPI[IO]]
      for {
        res <- admin.getConfig
        _ = res shouldBe objects.get[FullConfig]
      } yield ()
    }
  }
}
