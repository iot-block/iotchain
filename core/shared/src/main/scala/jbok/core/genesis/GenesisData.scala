package jbok.core.genesis

import jbok.core.models.{Block, BlockBody, BlockHeader}
import jbok.crypto.authds.mpt.MPTrie
import scodec.bits.ByteVector

case class AllocAccount(balance: String)

case class GenesisData(
    nonce: ByteVector,
    mixHash: Option[ByteVector],
    difficulty: String,
    extraData: ByteVector,
    gasLimit: String,
    coinbase: ByteVector,
    timestamp: String,
    alloc: Map[String, AllocAccount]
)

object Genesis {
  val header: BlockHeader = BlockHeader(
    ByteVector.empty,
    ByteVector.empty,
    ByteVector.empty,
    MPTrie.emptyRootHash,
    MPTrie.emptyRootHash,
    MPTrie.emptyRootHash,
    ByteVector.empty,
    0,
    0,
    0,
    0,
    System.currentTimeMillis(),
    ByteVector.empty,
    ByteVector.empty,
    ByteVector.empty
  )

  val block: Block = Block(header, BlockBody(Nil, Nil))

}
