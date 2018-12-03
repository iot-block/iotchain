package jbok.core.peer.discovery

import java.net.{InetAddress, InetSocketAddress}

import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.crypto._
import jbok.crypto.signature.{CryptoSignature, ECDSA, KeyPair, Signature}
import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs.{discriminated, uint8}

import scala.util.Try

sealed trait KadPacket {
  lazy val hash: ByteVector = Codec.encode(this).require.bytes.kec256
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

case class UdpPacket(hash: ByteVector, signature: CryptoSignature, kadPacket: KadPacket) {
  // get?
  def pk: KeyPair.Public =
    Signature[ECDSA].recoverPublic(kadPacket.hash.toArray, signature, 0).get

  lazy val id: ByteVector = pk.bytes.kec256

  def isValid: Boolean =
    hash == (pk.bytes ++ kadPacket.hash).kec256
}
