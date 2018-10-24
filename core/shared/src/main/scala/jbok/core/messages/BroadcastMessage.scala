package jbok.core.messages

import jbok.core.models.{Block, SignedTransaction}
import scodec.bits.ByteVector

case class Handshake(
    version: Int,
    listenPort: Int,
    id: ByteVector
) extends Message

case class Status(
    chainId: Int,
    genesisHash: ByteVector,
    bestNumber: BigInt
) extends Message {
  def isCompatible(other: Status): Boolean =
    chainId == other.chainId && genesisHash == other.genesisHash
}

case class BlockHash(hash: ByteVector, number: BigInt)
case class NewBlockHashes(hashes: List[BlockHash])          extends Message
case class NewBlock(block: Block)                           extends Message
case class SignedTransactions(txs: List[SignedTransaction]) extends Message
