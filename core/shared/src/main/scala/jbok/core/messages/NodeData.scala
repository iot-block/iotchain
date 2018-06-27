package jbok.core.messages

import scodec.bits.ByteVector

case class NodeData(values: Seq[ByteVector])
