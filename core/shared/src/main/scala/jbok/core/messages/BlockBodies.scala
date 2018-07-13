package jbok.core.messages

import jbok.core.models.BlockBody
import scodec.Codec
import scodec.bits.ByteVector
import jbok.codec.codecs._

case class GetBlockBodies(hashes: List[ByteVector]) extends Message
object GetBlockBodies {
  implicit val codec: Codec[GetBlockBodies] = codecList[ByteVector].as[GetBlockBodies]
}

case class BlockBodies(bodies: List[BlockBody]) extends Message
object BlockBodies {
  implicit val codec: Codec[BlockBodies] = codecList[BlockBody].as[BlockBodies]
}
