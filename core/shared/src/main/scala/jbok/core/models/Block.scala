package jbok.core.models

case class Block(header: BlockHeader, body: BlockBody) {
  lazy val id: String = s"Block(${header.hash.toHex.take(7)})@${header.number}"
}
