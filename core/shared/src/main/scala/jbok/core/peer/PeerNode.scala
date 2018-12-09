package jbok.core.peer
import java.net._

import jbok.codec.rlp.RlpCodec
import jbok.crypto._
import jbok.crypto.signature.KeyPair
import scodec.bits.ByteVector
import scodec.codecs._

import scala.util.Try

sealed abstract class PeerType(val priority: Int)
object PeerType {
  case object Discovered extends PeerType(1)
  case object Static     extends PeerType(2)
  case object Trusted    extends PeerType(3)

  implicit val codec: RlpCodec[PeerType] =
    RlpCodec.item(mappedEnum(uint8, Discovered -> 1, Static -> 2, Trusted -> 3))
}

final case class PeerNode(
    pk: KeyPair.Public,
    host: String,
    tcpPort: Int,
    udpPort: Int,
    peerType: PeerType = PeerType.Trusted
) {
  lazy val id         = pk.bytes.kec256
  lazy val tcpAddress = new InetSocketAddress(host, tcpPort)
  lazy val udpAddress = new InetSocketAddress(host, udpPort)
  lazy val uri = {
    val host = tcpAddress.getAddress match {
      case _: Inet6Address => s"[${tcpAddress.getHostName}]"
      case _               => tcpAddress.getHostName
    }
    val port = tcpAddress.getPort
    new URI(s"${PeerNode.NodeScheme}://${pk.bytes.toHex}@$host:$port")
  }
  override def toString: String = s"PeerNode(#${id.toHex.take(7)})"
}

object PeerNode {
  val NodeScheme = "jbok"

  val PublicLength = 64

  def fromTcpAddr(pk: KeyPair.Public, addr: InetSocketAddress): PeerNode =
    PeerNode(pk, addr.getHostName, addr.getPort, 0)

  def fromUri(uri: URI): PeerNode = {
    val pk   = KeyPair.Public(uri.getUserInfo)
    val addr = new InetSocketAddress(uri.getHost, uri.getPort)
    fromTcpAddr(pk, addr)
  }

  /**
    * Parse a node string, for it to be valid it should have the format:
    * "[scheme]://[128 char (64bytes) hex string]@[IPv4 address | '['IPv6 address']' ]:[port]"
    *
    * @param node to be parsed
    * @return the parsed node, or the error detected during parsing
    */
  def fromStr(node: String): Either[Throwable, PeerNode] = {
    def checkURI(node: String) = Try(new URI(node)).toEither

    def checkScheme(uri: URI) =
      if (uri.getScheme == NodeScheme) Right(NodeScheme) else Left(new Exception("invalid scheme"))

    def checkPk(uri: URI) =
      ByteVector
        .fromHex(uri.getUserInfo)
        .map(hex => {
          if (hex.length == PublicLength) Right(KeyPair.Public(hex))
          else Left(new Exception("invalid pk length"))
        })
        .getOrElse(Left(new Exception("invalid pk")))

    def checkAddress(uri: URI) =
      for {
        (host, port) <- Try(InetAddress.getByName(uri.getHost) -> uri.getPort).toEither
        host2 <- host match {
          case _: Inet4Address | _: Inet6Address => Right(host)
          case _ =>
            Left(new Exception(s"invalid host ${host}"))
        }
        addr <- Try(new InetSocketAddress(host2, port)).toEither
      } yield addr

    for {
      uri  <- checkURI(node)
      _    <- checkScheme(uri)
      pk   <- checkPk(uri)
      addr <- checkAddress(uri)
    } yield PeerNode.fromTcpAddr(pk, addr)
  }
}
