package jbok.core.messages

import jbok.core.models.BlockHeader
import scodec.bits.ByteVector

case class GetBlockHeaders(block: Either[BigInt, ByteVector], maxHeaders: BigInt, skip: BigInt, reverse: Boolean)
    extends Message

case class BlockHeaders(headers: List[BlockHeader]) extends Message
