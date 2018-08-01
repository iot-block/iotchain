package jbok.core.messages

import scodec.bits.ByteVector

case class BlockHash(hash: ByteVector, number: BigInt) extends Message
case class NewBlockHashes(hashes: List[BlockHash]) extends Message
