package jbok.core.messages

import scodec.bits.ByteVector

case class GetNodeData(mptElementsHashes: Seq[ByteVector])
