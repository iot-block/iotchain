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

case class PeerNode(pk: KeyPair.Public, host: String, port: Int, peerType: PeerType = PeerType.Trusted) {
  lazy val id   = pk.bytes.kec256
  lazy val addr = new InetSocketAddress(host, port)
  lazy val uri = {
    val host = addr.getAddress match {
      case _: Inet6Address => s"[${addr.getHostName}]"
      case _               => addr.getHostName
    }
    val port = addr.getPort
    new URI(s"${PeerNode.NodeScheme}://${pk.bytes.toHex}@$host:$port")
  }
}

object PeerNode {
  val NodeScheme = "jbok"

  val PublicLength = 64

  def fromAddr(pk: KeyPair.Public, addr: InetSocketAddress): PeerNode =
    PeerNode(pk, addr.getHostName, addr.getPort)

  def fromUri(uri: URI): PeerNode = {
    val pk = KeyPair.Public(uri.getUserInfo)
    PeerNode(pk, uri.getHost, uri.getPort)
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
      uri     <- checkURI(node)
      _       <- checkScheme(uri)
      nodeId  <- checkPk(uri)
      address <- checkAddress(uri)
    } yield PeerNode(nodeId, address.getHostName, address.getPort)
  }
}
