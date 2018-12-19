package jbok.app.api.impl

import cats.effect.IO
import jbok.app.api.AdminAPI
import jbok.core.peer.{PeerManager, PeerNode}

final class AdminApiImpl(
    peerManager: PeerManager[IO]
) extends AdminAPI[IO] {
  private[this] val log = org.log4s.getLogger("AdminAPI")

  override def stop: IO[Unit] = ???

  override def peerNodeUri: IO[String] = IO.pure(peerManager.peerNode.uri.toString)

  override def addPeer(peerNodeUri: String): IO[Unit] = {
    val peerNode = PeerNode.fromStr(peerNodeUri)
    if (peerNode.isRight) {
      log.info(s"add a peer: ${peerNodeUri}")
      peerManager.addPeerNode(peerNode.right.get)
    } else {
      IO.pure(Unit)
    }
  }

  override def dropPeer(peerNode: String): IO[Unit] = ???
}

object AdminApiImpl {
  def apply(peerManager: PeerManager[IO]): AdminAPI[IO] =
    new AdminApiImpl(peerManager)
}
