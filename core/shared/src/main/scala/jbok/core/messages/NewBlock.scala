package jbok.core.messages

import jbok.core.models.Block
import scodec.Codec

case class NewBlock(block: Block) extends Message
object NewBlock {
  implicit val codec: Codec[NewBlock] = Codec[Block].as[NewBlock]
}

