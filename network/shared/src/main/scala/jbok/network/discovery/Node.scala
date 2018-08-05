package jbok.network.discovery

import java.net.{InetSocketAddress, _}

import scodec.bits.ByteVector

import scala.util.Try

case class Node(id: ByteVector, addr: InetSocketAddress) {
  def toUri: URI = {
    val host = addr.getAddress match {
      case _: Inet6Address => s"[${addr.getHostName}]"
      case _               => addr.getHostName
    }
    val port = addr.getPort
    new URI(s"${Node.NodeScheme}://${id.toHex}@$host:$port")
  }
}

object Node {
  val NodeScheme = "node"
  val NodeIdSize = 64
  def fromUri(uri: URI): Node = {
    val nodeId = ByteVector.fromValidHex(uri.getUserInfo)
    Node(nodeId, new InetSocketAddress(uri.getHost, uri.getPort))
  }

  /**
    * Parse a node string, for it to be valid it should have the format:
    * "[scheme]://[128 char (64bytes) hex string]@[IPv4 address | '['IPv6 address']' ]:[port]"
    *
    * @param node to be parsed
    * @return the parsed node, or the error detected during parsing
    */
  def parseNode(node: String): Either[Throwable, Node] = {
    def checkURI(node: String) = Try(new URI(node)).toEither

    def checkScheme(uri: URI) =
      if (uri.getScheme == NodeScheme) Right(NodeScheme) else Left(new Exception("invalid scheme"))

    def checkNodeId(uri: URI) =
      ByteVector
        .fromHex(uri.getUserInfo)
        .map(nodeId => {
          if (nodeId.length == NodeIdSize) Right(nodeId)
          else Left(new Exception("invalid nodeId length"))
        })
        .getOrElse(Left(new Exception("invalid nodeId")))

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
      uri <- checkURI(node)
      _ <- checkScheme(uri)
      nodeId <- checkNodeId(uri)
      address <- checkAddress(uri)
    } yield Node(nodeId, address)
  }

  /**
    * Parses a set of nodes, logging the invalid ones and returning the valid ones
    *
    * @param unParsedNodes, nodes to be parsed
    * @return set of parsed and valid nodes
    */
  def parseNodes(unParsedNodes: Set[String]): Set[Node] = unParsedNodes.foldLeft[Set[Node]](Set.empty) {
    case (parsedNodes, nodeString) =>
      val maybeNode = parseNode(nodeString)
      maybeNode match {
        case Left(_)     => parsedNodes
        case Right(node) => parsedNodes + node
      }
  }
}
