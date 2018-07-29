package jbok.core.messages

import jbok.crypto.authds.mpt.Node
import scodec.bits.ByteVector

case class NodeData(values: List[ByteVector]) extends Message {
  def getMptNode(idx: Int): Node = ???
}
