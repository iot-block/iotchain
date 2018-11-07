package jbok.core.messages

import java.util.UUID

import jbok.core.models.{BlockBody, BlockHeader, Receipt}
import jbok.core.sync.NodeHash
import scodec.bits.ByteVector

sealed trait SyncMessage extends Message {
  def id: String
}

sealed trait SyncRequest  extends SyncMessage
sealed trait SyncResponse extends SyncMessage

case class GetBlockBodies(hashes: List[ByteVector], id: String = UUID.randomUUID().toString) extends SyncRequest
case class BlockBodies(bodies: List[BlockBody], id: String)                                  extends SyncResponse

case class GetBlockHeaders(
    block: Either[BigInt, ByteVector],
    maxHeaders: Int,
    skip: Int,
    reverse: Boolean,
    id: String = UUID.randomUUID().toString
) extends SyncRequest
case class BlockHeaders(headers: List[BlockHeader], id: String) extends SyncResponse

case class GetReceipts(blockHashes: List[ByteVector], id: String = UUID.randomUUID().toString) extends SyncRequest
case class Receipts(receiptsForBlocks: List[List[Receipt]], id: String)                        extends SyncResponse

case class GetNodeData(nodeHashes: List[NodeHash], id: String = UUID.randomUUID().toString) extends SyncRequest
case class NodeData(values: List[ByteVector], id: String)                               extends SyncResponse
