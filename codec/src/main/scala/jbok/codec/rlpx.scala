package jbok.codec

package object rlpx {
  sealed abstract class PacketType(val id: Int)
  object PacketType {

    case object Ping          extends PacketType(1)
    case object Pong          extends PacketType(2)
    case object FindNeighbors extends PacketType(3)
    case object Neighbors     extends PacketType(4)

    val map = Seq(Ping, Pong, FindNeighbors, Neighbors).map(v => (v -> v.id)).toMap
  }

  sealed abstract class Packet
  object Packet {

    case class Header[P <: Packet](hash: String, signature: String, payload: P)

    case class Ping(hash: String) extends Packet
    case class Pong(hash: String) extends Packet
    case class FindNeighbors(hash: String) extends Packet
    case class Neighbors(hash: String) extends Packet

  }


}
