package jbok.core

import enumeratum._
import jbok.core.peer.Peer
sealed trait NodeStatus extends EnumEntry
object NodeStatus extends Enum[NodeStatus] {
  val values = findValues

  final case object WaitForPeers                extends NodeStatus
  final case class Syncing[F[_]](peer: Peer[F]) extends NodeStatus
  final case object Done                        extends NodeStatus
}
