package jbok.core.models

import scodec.Codec

case class Block(header: BlockHeader, body: BlockBody) {
  def id: String = s"Block(${header.hash.toHex.take(7)})@${header.number}"
}
object Block {
  implicit val codec: Codec[Block] = (Codec[BlockHeader] :: Codec[BlockBody]).as[Block]
}
