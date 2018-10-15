package jbok.core.messages

import java.util.UUID

import jbok.core.models.{BlockBody, BlockHeader, Receipt}
import jbok.crypto.authds.mpt.Node
import scodec.bits.ByteVector

sealed trait SyncMessage extends Message {
  def id: String
}

case class GetBlockBodies(hashes: List[ByteVector], id: String = UUID.randomUUID().toString) extends SyncMessage
case class BlockBodies(bodies: List[BlockBody], id: String)                                  extends SyncMessage

case class GetBlockHeaders(
    block: Either[BigInt, ByteVector],
    maxHeaders: Int,
    skip: Int,
    reverse: Boolean,
    id: String = UUID.randomUUID().toString
) extends SyncMessage
case class BlockHeaders(headers: List[BlockHeader], id: String) extends SyncMessage

case class GetReceipts(blockHashes: List[ByteVector], id: String = UUID.randomUUID().toString) extends SyncMessage
case class Receipts(receiptsForBlocks: List[List[Receipt]], id: String)                        extends SyncMessage

case class GetNodeData(mptElementsHashes: List[ByteVector], id: String = UUID.randomUUID().toString) extends SyncMessage
case class NodeData(values: List[ByteVector], id: String) extends SyncMessage {
  def getMptNode(idx: Int): Node = ???
}
