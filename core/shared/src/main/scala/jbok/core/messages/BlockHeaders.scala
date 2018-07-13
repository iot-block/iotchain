package jbok.core.messages

import jbok.core.models.BlockHeader
import scodec.Codec
import scodec.bits.ByteVector
import jbok.codec.codecs._

case class GetBlockHeaders(block: Either[BigInt, ByteVector], maxHeaders: BigInt, skip: BigInt, reverse: Boolean)
    extends Message

object GetBlockHeaders {
  implicit val codec: Codec[GetBlockHeaders] =
    (codecEither[BigInt, ByteVector] :: codecBigInt :: codecBigInt :: codecBoolean).as[GetBlockHeaders]
}

case class BlockHeaders(headers: List[BlockHeader]) extends Message
object BlockHeaders {
  implicit val codec: Codec[BlockHeaders] = codecList[BlockHeader].as[BlockHeaders]
}
