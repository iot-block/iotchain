package jbok.core.sync

import jbok.codec.rlp.RlpCodec
import jbok.core.models.BlockHeader
import scodec.bits.ByteVector
import scodec.codecs.{bytes, discriminated, uint8}

sealed trait HashType {
  def v: ByteVector
}

object HashType {
  implicit val codec: RlpCodec[HashType] = RlpCodec.item(
    discriminated[HashType]
      .by(uint8)
      .subcaseO(1) {
        case t: StateMptNodeHash => Some(t)
        case _                   => None
      }(bytes.as[StateMptNodeHash])
      .subcaseO(2) {
        case t: ContractStorageMptNodeHash => Some(t)
        case _                             => None
      }(bytes.as[ContractStorageMptNodeHash])
      .subcaseO(3) {
        case t: EvmCodeHash => Some(t)
        case _              => None
      }(bytes.as[EvmCodeHash])
      .subcaseO(4) {
        case t: StorageRootHash => Some(t)
        case _                  => None
      }(bytes.as[StorageRootHash])
  )
}

case class StateMptNodeHash(v: ByteVector) extends HashType

case class ContractStorageMptNodeHash(v: ByteVector) extends HashType

case class EvmCodeHash(v: ByteVector) extends HashType

case class StorageRootHash(v: ByteVector) extends HashType

case class SyncState(
    targetBlock: BlockHeader,
    pendingMptNodes: List[HashType] = Nil,
    pendingNonMptNodes: List[HashType] = Nil,
    blockBodiesQueue: List[ByteVector] = Nil,
    receiptsQueue: List[ByteVector] = Nil,
    downloadedNodesCount: Int = 0,
    bestBlockHeaderNumber: BigInt = 0
) {
  def enqueueBlockBodies(blockBodies: List[ByteVector]): SyncState =
    copy(blockBodiesQueue = blockBodiesQueue ++ blockBodies)

  def enqueueReceipts(receipts: List[ByteVector]): SyncState =
    copy(receiptsQueue = receiptsQueue ++ receipts)

  def addPendingNodes(hashes: List[HashType]): SyncState = {
    val (mpt, nonMpt) = hashes.partition {
      case _: StateMptNodeHash | _: ContractStorageMptNodeHash => true
      case _: EvmCodeHash | _: StorageRootHash                 => false
    }
    // Nodes are prepended in order to traverse mpt in-depth. For mpt nodes is not needed but to keep it consistent,
    // it was applied too
    copy(pendingMptNodes = mpt ++ pendingMptNodes, pendingNonMptNodes = nonMpt ++ pendingNonMptNodes)
  }

  def anythingQueued: Boolean =
    pendingNonMptNodes.nonEmpty ||
      pendingMptNodes.nonEmpty ||
      blockBodiesQueue.nonEmpty ||
      receiptsQueue.nonEmpty

  val totalNodesCount: Int = downloadedNodesCount + pendingMptNodes.size + pendingNonMptNodes.size
}
