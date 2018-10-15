package jbok.core.sync
import cats.effect.Effect
import jbok.core.peer.PeerManager

import scala.concurrent.ExecutionContext

case class FastSync[F[_]](peerManager: PeerManager[F])(implicit F: Effect[F], EC: ExecutionContext) {
  private[this] val log = org.log4s.getLogger
}
