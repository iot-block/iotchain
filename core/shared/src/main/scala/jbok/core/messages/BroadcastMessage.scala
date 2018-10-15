package jbok.core.messages

import jbok.core.models.{Block, SignedTransaction}
import scodec.bits.ByteVector

sealed trait BroadcastMessage extends Message

case class BlockHash(hash: ByteVector, number: BigInt)
case class NewBlockHashes(hashes: List[BlockHash]) extends BroadcastMessage
case class NewBlock(block: Block) extends BroadcastMessage
case class SignedTransactions(txs: List[SignedTransaction]) extends BroadcastMessage
