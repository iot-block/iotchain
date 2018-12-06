package jbok.core.peer.discovery

import java.util.UUID

import jbok.core.peer.PeerNode
import jbok.crypto.signature.KeyPair
import scodec.Codec
import scodec.codecs.{discriminated, uint8}
import jbok.codec.rlp.implicits._

sealed trait KadPacket {
  def id: UUID
}

object KadPacket {
  implicit val codec: Codec[KadPacket] =
    discriminated[KadPacket]
      .by(uint8)
      .subcaseO(1) {
        case t: Ping => Some(t)
        case _       => None
      }(Codec[Ping])
      .subcaseO(2) {
        case t: Pong => Some(t)
        case _       => None
      }(Codec[Pong])
      .subcaseO(3) {
        case t: FindNode => Some(t)
        case _           => None
      }(Codec[FindNode])
      .subcaseO(4) {
        case t: Neighbours => Some(t)
        case _             => None
      }(Codec[Neighbours])

  final case class Ping(from: PeerNode, expiration: Long, id: UUID)                              extends KadPacket
  final case class Pong(from: PeerNode, expiration: Long, id: UUID)                              extends KadPacket
  final case class FindNode(from: PeerNode, pk: KeyPair.Public, expiration: Long, id: UUID)      extends KadPacket
  final case class Neighbours(from: PeerNode, nodes: List[PeerNode], expiration: Long, id: UUID) extends KadPacket
}
