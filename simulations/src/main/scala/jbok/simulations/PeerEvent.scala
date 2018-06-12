package jbok.simulations

sealed abstract class PeerEvent(id: NodeId)
object PeerEvent {
  case class PeerAdd(id: NodeId) extends PeerEvent(id)
  case class PeerDrop(id: NodeId) extends PeerEvent(id)
  case class PeerSend(id: NodeId, msgCode: Long) extends PeerEvent(id)
  case class PeerRecv(id: NodeId, msgCode: Long) extends PeerEvent(id)
}
