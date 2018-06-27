package jbok.core.messages

import scodec.bits.ByteVector

case class BlockHash(hash: ByteVector, number: BigInt)
