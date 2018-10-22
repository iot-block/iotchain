package jbok.core.peer.discovery

import java.net.{InetAddress, InetSocketAddress}

import cats.effect.Sync
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.codecs._
import jbok.core.peer.PeerNode
import jbok.core.peer.discovery.UdpPacket._
import jbok.crypto._
import jbok.crypto.signature.{CryptoSignature, ECDSA, KeyPair, Signature}
import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs.{discriminated, uint8}

import scala.concurrent.duration._
import scala.util.Try

sealed trait KadPacket
object KadPacket {
  implicit val codec: RlpCodec[KadPacket] = RlpCodec.item(
    discriminated[KadPacket]
      .by(uint8)
      .subcaseO(1) {
        case t: Ping => Some(t)
        case _       => None
      }(RlpCodec[Ping].codec)
      .subcaseO(2) {
        case t: Pong => Some(t)
        case _       => None
      }(RlpCodec[Pong].codec)
      .subcaseO(3) {
        case t: FindNode => Some(t)
        case _           => None
      }(RlpCodec[FindNode].codec)
      .subcaseO(4) {
        case t: Neighbours => Some(t)
        case _             => None
      }(RlpCodec[Neighbours].codec)
  )
}

case class Ping(version: Int, from: Endpoint, to: Endpoint, expiration: Long) extends KadPacket
case class Pong(to: Endpoint, pingHash: ByteVector, expiration: Long)         extends KadPacket
case class FindNode(pk: KeyPair.Public, expiration: Long)                     extends KadPacket
case class Neighbours(nodes: List[Neighbour], expiration: Long)               extends KadPacket
case class Neighbour(endpoint: Endpoint, pk: KeyPair.Public)

case class Endpoint(ip: ByteVector, udpPort: Int, tcpPort: Int) {
  val addr                      = Endpoint.toUdpAddress(this).get
  override def toString: String = s"${Endpoint.toUdpAddress(this).get}/${tcpPort}"
}
object Endpoint {
  def makeEndpoint(udpAddress: InetSocketAddress, tcpPort: Int): Endpoint =
    Endpoint(ByteVector(udpAddress.getAddress.getAddress), udpAddress.getPort, tcpPort)

  def toUdpAddress(endpoint: Endpoint): Option[InetSocketAddress] = {
    val addr = Try(InetAddress.getByAddress(endpoint.ip.toArray)).toOption
    addr.map(address => new InetSocketAddress(address, endpoint.udpPort))
  }
}

case class UdpPacket(bytes: ByteVector) extends AnyVal {
  def pk: KeyPair.Public = {
    val msgHash = bytes.drop(MdcLength + 65).kec256
    val sig     = signature
    Signature[ECDSA].recoverPublic(msgHash.toArray, sig, None).get
  }

  def id: ByteVector = pk.bytes.kec256

  def data: ByteVector = bytes.drop(UdpPacket.DataOffset)

  def packetType: Byte = bytes(UdpPacket.PacketTypeByteIndex)

  def mdc: ByteVector = bytes.take(UdpPacket.MdcLength)

  def signature: CryptoSignature = {
    val signatureBytes = bytes.drop(UdpPacket.MdcLength).take(65)
    val r              = signatureBytes.take(32)
    val s              = signatureBytes.drop(32).take(32)
    val v              = (signatureBytes.last + 27).toByte

    CryptoSignature(r, s, v)
  }

  def kadPacket[F[_]: Sync]: F[KadPacket] =
    Sync[F].delay(RlpCodec.decode[KadPacket](data.bits).require.value)
}
object UdpPacket {
  implicit val codec: Codec[UdpPacket] = implicitly[RlpCodec[UdpPacket]].codec
  private val MdcLength           = 32
  private val PacketTypeByteIndex = MdcLength + 65
  private val DataOffset          = PacketTypeByteIndex
}
