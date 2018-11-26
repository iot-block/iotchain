package jbok.core.config

import jbok.core.models._
import jbok.crypto.authds.mpt.MerklePatriciaTrie
import scodec.bits._

case class GenesisConfig(
    nonce: ByteVector,
    mixHash: Option[ByteVector],
    difficulty: String,
    extraData: ByteVector,
    gasLimit: String,
    coinbase: ByteVector,
    timestamp: Long,
    alloc: Map[String, BigInt],
    chainId: Byte
) {
  lazy val header = BlockHeader(
    ByteVector.empty,
    ByteVector.empty,
    coinbase,
    MerklePatriciaTrie.emptyRootHash,
    MerklePatriciaTrie.emptyRootHash,
    MerklePatriciaTrie.emptyRootHash,
    ByteVector.empty,
    BigInt(Integer.parseInt(difficulty.replace("0x", ""), 16)),
    0,
    BigInt(Integer.parseInt(gasLimit.replace("0x", ""), 16)),
    0,
    timestamp,
    extraData,
    mixHash.getOrElse(ByteVector.empty),
    nonce
  )

  lazy val body = BlockBody(Nil, Nil)

  lazy val block = Block(header, body)
}

object GenesisConfig {
  val default = GenesisConfig(
    nonce = hex"0x42",
    mixHash = Some(hex"0x0000000000000000000000000000000000000000000000000000000000000000"),
    difficulty = "0x0400",
    extraData = hex"0x11bbe8db4e347b4e8c937c1c8370e4b5ed33adb3db69cbdb7a38e1e50b1b82fa",
    gasLimit = "0xff1388",
    coinbase = hex"0x0000000000000000000000000000000000000000",
    timestamp = System.currentTimeMillis(),
    alloc = Map(
      "d7a681378321f472adffb9fdded2712f677e0ba9" -> BigInt("1000000000000000000000000000000000000000000")
    ),
    chainId = 0.toByte
  )
}
