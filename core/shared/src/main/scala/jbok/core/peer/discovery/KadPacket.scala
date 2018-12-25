package jbok.core.peer.discovery

import java.util.UUID

import jbok.codec.rlp.implicits._
import jbok.core.peer.PeerNode
import jbok.crypto.signature.KeyPair

sealed trait KadPacket {
  def id: UUID
}

object KadPacket {
  final case class Ping(from: PeerNode, expiration: Long, id: UUID)                              extends KadPacket
  final case class Pong(from: PeerNode, expiration: Long, id: UUID)                              extends KadPacket
  final case class FindNode(from: PeerNode, pk: KeyPair.Public, expiration: Long, id: UUID)      extends KadPacket
  final case class Neighbours(from: PeerNode, nodes: List[PeerNode], expiration: Long, id: UUID) extends KadPacket

  implicit val codec = RlpCodec[KadPacket]
}
