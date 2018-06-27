package jbok.core.models

import scodec.Codec

case class Block(header: BlockHeader, body: BlockBody)
object Block {
  implicit val codec: Codec[Block] = (Codec[BlockHeader] :: Codec[BlockBody]).as[Block]
}
