package jbok.core.messages

import scodec.bits.ByteVector

case class GetBlockHeaders(block: Either[BigInt, ByteVector], maxHeaders: BigInt, skip: BigInt, reverse: Boolean)
    extends Message
