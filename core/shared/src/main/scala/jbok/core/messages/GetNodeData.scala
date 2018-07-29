package jbok.core.messages

import scodec.bits.ByteVector

case class GetNodeData(mptElementsHashes: List[ByteVector]) extends Message
