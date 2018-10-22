package jbok.core.peer
import java.net._

import jbok.crypto.signature.KeyPair
import scodec.bits.ByteVector
import jbok.crypto._

import scala.util.Try

case class PeerNode(pk: KeyPair.Public, host: String, port: Int) {
  lazy val id = pk.bytes.kec256

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
  def apply(pk: KeyPair.Public, addr: InetSocketAddress): PeerNode =
    PeerNode(pk, addr.getHostName, addr.getPort)

  val NodeScheme   = "jbok"
  val PublicLength = 64
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
  def parseStr(node: String): Either[Throwable, PeerNode] = {
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

  /**
    * Parses a set of nodes, logging the invalid ones and returning the valid ones
    *
    * @param unParsedNodes, nodes to be parsed
    * @return set of parsed and valid nodes
    */
  def parseNodes(unParsedNodes: Set[String]): Set[PeerNode] = unParsedNodes.foldLeft[Set[PeerNode]](Set.empty) {
    case (parsedNodes, nodeString) =>
      val maybeNode = parseStr(nodeString)
      maybeNode match {
        case Left(_)     => parsedNodes
        case Right(node) => parsedNodes + node
      }
  }
}
