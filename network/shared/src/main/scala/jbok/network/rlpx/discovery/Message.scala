package jbok.network.rlpx.discovery

import scodec.bits.ByteVector

sealed trait Message {
  def packetType: Byte
}

case class Endpoint(address: ByteVector, udpPort: Int, tcpPort: Int)

case class Ping(version: Int, from: Endpoint, to: Endpoint, timestamp: Long) extends Message {
  override val packetType: Byte = 0x01
}

case class Pong(to: Endpoint, token: ByteVector, timestamp: Long) extends Message {
  override val packetType: Byte = 0x02
}

case class FindNode(target: ByteVector, expires: Long) extends Message {
  override val packetType: Byte = 0x03
}

case class Neighbour(endpoint: Endpoint, nodeId: ByteVector)
case class Neighbours(nodes: List[Neighbour], expires: Long) extends Message {
  override val packetType: Byte = 0x04
}
