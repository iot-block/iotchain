package jbok.core.messages

import jbok.core.models.{Block, SignedTransaction}
import scodec.bits.ByteVector

case class Handshake(
    version: Int,
    listenPort: Int,
    id: ByteVector
) extends Message

case class Status(
    networkId: Int,
    genesisHash: ByteVector,
    bestHash: ByteVector,
    bestNumber: BigInt,
    totalDifficulty: BigInt
) extends Message

case class BlockHash(hash: ByteVector, number: BigInt)
case class NewBlockHashes(hashes: List[BlockHash])          extends Message
case class NewBlock(block: Block)                           extends Message
case class SignedTransactions(txs: List[SignedTransaction]) extends Message
