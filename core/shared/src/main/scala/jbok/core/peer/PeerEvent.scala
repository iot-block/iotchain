package jbok.core.peer

import jbok.core.messages.Message

sealed trait PeerEvent {
  val peerId: PeerId
}

object PeerEvent {
  case class PeerAdd(peerId: PeerId) extends PeerEvent
  case class PeerDrop(peerId: PeerId) extends PeerEvent
  case class PeerRecv(peerId: PeerId, message: Message) extends PeerEvent
}
