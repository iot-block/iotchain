package jbok.core.messages

import jbok.core.models._
import jbok.core.sync.NodeHash
import jbok.crypto.signature.CryptoSignature
import scodec.bits.ByteVector

final case class Handshake(
    version: Int,
    listenPort: Int,
    sid: ByteVector
)

final case class Status(
    chainId: BigInt,
    genesisHash: ByteVector,
    bestNumber: BigInt
) {
  def isCompatible(other: Status): Boolean =
    chainId == other.chainId && genesisHash == other.genesisHash
}

final case class BlockHash(hash: ByteVector, number: BigInt)
final case class NewBlockHashes(hashes: List[BlockHash])
final case class SignedTransactions(txs: List[SignedTransaction])

final case class GetBlockHeaders(
    block: Either[BigInt, ByteVector],
    maxHeaders: Int,
    skip: Int,
    reverse: Boolean
)
final case class BlockHeaders(headers: List[BlockHeader])

final case class GetBlockBodies(hashes: List[ByteVector])
final case class BlockBodies(bodies: List[BlockBody])
final case class NewBlock(block: Block)

final case class GetNodeData(nodeHashes: List[NodeHash])
final case class NodeData(values: List[ByteVector])

final case class GetReceipts(blockHashes: List[ByteVector])
final case class Receipts(receiptsForBlocks: List[List[Receipt]])

final case class AuthPacket(bytes: ByteVector)

final case class IstanbulMessage(
    msgCode: Int,
    msg: ByteVector,
    address: Address,
    signature: ByteVector,
    committedSig: Option[CryptoSignature]
)

object IstanbulMessage {
  val msgPreprepareCode = 0
  val msgPrepareCode    = 1
  val msgCommitCode     = 2
  val msgRoundChange    = 3
  val msgAll            = 4
}
