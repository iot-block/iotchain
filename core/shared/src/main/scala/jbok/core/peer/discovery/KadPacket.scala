package jbok.core.peer.discovery

import jbok.core.peer.PeerNode
import jbok.crypto.signature.KeyPair

object KadPacket {
  final case class Ping(from: PeerNode, expiration: Long)
  final case class Pong(from: PeerNode, expiration: Long)
  final case class FindNode(from: PeerNode, pk: KeyPair.Public, expiration: Long)
  final case class Neighbours(from: PeerNode, nodes: List[PeerNode], expiration: Long)
}
