package jbok.core.sync

import jbok.codec.codecs._
import jbok.core.models.BlockHeader
import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs.{discriminated, uint8}

sealed trait HashType {
  def v: ByteVector
}

object HashType {
  implicit val codec: Codec[HashType] = discriminated[HashType]
    .by(uint8)
    .subcaseO(1) {
      case t: StateMptNodeHash => Some(t)
      case _                   => None
    }(codecBytes.as[StateMptNodeHash])
    .subcaseO(2) {
      case t: ContractStorageMptNodeHash => Some(t)
      case _                             => None
    }(codecBytes.as[ContractStorageMptNodeHash])
    .subcaseO(3) {
      case t: EvmCodeHash => Some(t)
      case _              => None
    }(codecBytes.as[EvmCodeHash])
    .subcaseO(4) {
      case t: StorageRootHash => Some(t)
      case _                  => None
    }(codecBytes.as[StorageRootHash])
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

object SyncState {

  import jbok.codec.codecs._

  implicit val codec: Codec[SyncState] = (
    Codec[BlockHeader] ::
      codecList[HashType] ::
      codecList[HashType] ::
      codecList[ByteVector] ::
      codecList[ByteVector] ::
      codecBigInt.xmap[Int](_.toInt, BigInt.apply) ::
      codecBigInt
  ).as[SyncState]
}
