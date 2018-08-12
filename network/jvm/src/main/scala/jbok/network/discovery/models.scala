package jbok.network.discovery

import java.net.{InetAddress, InetSocketAddress}

import jbok.crypto._
import jbok.crypto.signature.{CryptoSignature, SecP256k1}
import scodec.bits.ByteVector

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

sealed trait KadPacket {
  def packetType: Byte
}

case class Endpoint(ip: ByteVector, udpPort: Int, tcpPort: Int)
object Endpoint {
  def makeEndpoint(udpAddress: InetSocketAddress, tcpPort: Int): Endpoint =
    Endpoint(ByteVector(udpAddress.getAddress.getAddress), udpAddress.getPort, tcpPort)

  def toUdpAddress(endpoint: Endpoint): Option[InetSocketAddress] = {
    val addr = Try(InetAddress.getByAddress(endpoint.ip.toArray)).toOption
    addr.map(address => new InetSocketAddress(address, endpoint.udpPort))
  }
}

case class Ping(version: Int, from: Endpoint, to: Endpoint, expiration: Long) extends KadPacket {
  override val packetType: Byte = Ping.packetType
}
object Ping {
  val packetType: Byte = 0x01
}

case class Pong(to: Endpoint, pingHash: ByteVector, expiration: Long) extends KadPacket {
  override val packetType: Byte = Pong.packetType
}
object Pong {
  val packetType: Byte = 0x02
}

case class FindNode(target: ByteVector, expiration: Long) extends KadPacket {
  require(target.length == 65, "target should be a 65-byte public key")
  override val packetType: Byte = FindNode.packetType
}
object FindNode {
  val packetType: Byte = 0x03
}

case class Neighbour(endpoint: Endpoint, nodeId: ByteVector)
case class Neighbours(nodes: List[Neighbour], expiration: Long) extends KadPacket {
  override val packetType: Byte = Neighbours.packetType
}
object Neighbours {
  val packetType: Byte = 0x04
}

case class DiscoveryConfig(
    interface: String,
    port: Int,
    bootstrapNodes: Set[Node],
    nodesLimit: Int,
    scanMaxNodes: Int,
    scanInterval: FiniteDuration,
    messageExpiration: FiniteDuration
)

import jbok.network.discovery.UdpPacket._
case class UdpPacket(bytes: ByteVector) extends AnyVal {
  def nodeId: ByteVector = {
    val msgHash = bytes.drop(MdcLength + 65).kec256
    val sig = signature
    SecP256k1.recoverPublicBytes(sig.r, sig.s, sig.v.get, None, msgHash).get
  }

  def data: ByteVector = bytes.drop(UdpPacket.DataOffset)

  def packetType: Byte = bytes(UdpPacket.PacketTypeByteIndex)

  def mdc: ByteVector = bytes.take(UdpPacket.MdcLength)

  def signature: CryptoSignature = {
    val signatureBytes = bytes.drop(UdpPacket.MdcLength).take(65)
    val r = signatureBytes.take(32)
    val s = signatureBytes.drop(32).take(32)
    val v = (signatureBytes.last + 27).toByte

    CryptoSignature(r, s, Some(v))
  }
}

object UdpPacket {
  private val MdcLength = 32
  private val PacketTypeByteIndex = MdcLength + 65
  private val DataOffset = PacketTypeByteIndex + 1
}
