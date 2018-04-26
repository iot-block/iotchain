package jbok.p2p.peer

sealed trait PeerManagerOp
object PeerManagerOp {
  case object Discover extends PeerManagerOp
  case object Connect extends PeerManagerOp
}

sealed trait PeerManagerEvent
object PeerManagerEvent {}
