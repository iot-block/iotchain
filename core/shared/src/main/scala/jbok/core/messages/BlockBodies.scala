package jbok.core.messages

import jbok.core.models.BlockBody
import scodec.bits.ByteVector

case class GetBlockBodies(hashes: List[ByteVector]) extends Message

case class BlockBodies(bodies: List[BlockBody]) extends Message
