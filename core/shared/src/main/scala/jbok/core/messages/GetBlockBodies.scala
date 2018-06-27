package jbok.core.messages

import scodec.bits.ByteVector

case class GetBlockBodies(hashes: List[ByteVector]) extends Message
