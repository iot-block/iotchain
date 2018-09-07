package jbok.core.genesis

import jbok.core.Configs._
import jbok.core.models._
import jbok.crypto.authds.mpt.MPTrie
import scodec.bits.ByteVector

case class AllocAccount(balance: BigInt)

case class GenesisConfig(
    nonce: ByteVector,
    mixHash: Option[ByteVector],
    difficulty: String,
    extraData: ByteVector,
    gasLimit: String,
    coinbase: ByteVector,
    timestamp: Long,
    alloc: Map[String, AllocAccount]
)

object GenesisConfig {
  lazy val default: GenesisConfig =
    loadConfig[GenesisConfig]("jbok.genesis") match {
      case Left(e)  => throw new Exception(e.toString)
      case Right(c) => c
    }

  lazy val header: BlockHeader = genesisHeader(default)

  lazy val body: BlockBody = genesisBody(default)

  lazy val block: Block = Block(header, body)

  def genesisHeader(config: GenesisConfig): BlockHeader = BlockHeader(
    ByteVector.empty,
    ByteVector.empty,
    config.coinbase,
    MPTrie.emptyRootHash,
    MPTrie.emptyRootHash,
    MPTrie.emptyRootHash,
    ByteVector.empty,
    BigInt(Integer.parseInt(config.difficulty.replace("0x", ""), 16)),
    0,
    BigInt(Integer.parseInt(config.gasLimit.replace("0x", ""), 16)),
    0,
    System.currentTimeMillis(),
    config.extraData,
    config.mixHash.getOrElse(ByteVector.empty),
    config.nonce
  )

  def genesisBody(config: GenesisConfig): BlockBody = BlockBody(Nil, Nil)
}
