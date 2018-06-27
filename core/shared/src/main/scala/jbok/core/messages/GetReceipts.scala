package jbok.core.messages

import scodec.bits.ByteVector

case class GetReceipts(blockHashes: List[ByteVector]) extends Message
