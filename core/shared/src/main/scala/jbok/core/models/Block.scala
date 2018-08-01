package jbok.core.models

case class Block(header: BlockHeader, body: BlockBody) {
  def id: String = s"Block(${header.hash.toHex.take(7)})@${header.number}"
}
