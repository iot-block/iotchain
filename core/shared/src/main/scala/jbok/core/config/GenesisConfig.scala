package jbok.core.config

import io.circe.generic.JsonCodec
import jbok.codec.json.implicits._
import jbok.core.models._
import jbok.crypto.authds.mpt.MerklePatriciaTrie
import scodec.bits._

@JsonCodec
case class GenesisConfig(
    nonce: String,
    difficulty: String,
    extraData: String,
    gasLimit: String,
    coinbase: String,
    timestamp: Long = System.currentTimeMillis(),
    alloc: Map[String, String] = Map.empty,
    chainId: BigInt = 0
) {
  lazy val header = BlockHeader(
    parentHash = ByteVector.empty,
    ommersHash = ByteVector.empty,
    beneficiary = ByteVector.fromValidHex(coinbase),
    stateRoot = MerklePatriciaTrie.emptyRootHash,
    transactionsRoot = MerklePatriciaTrie.emptyRootHash,
    receiptsRoot = MerklePatriciaTrie.emptyRootHash,
    logsBloom = ByteVector.empty,
    difficulty = BigInt(Integer.parseInt(difficulty.replace("0x", ""), 16)),
    number = 0,
    gasLimit = BigInt(Integer.parseInt(gasLimit.replace("0x", ""), 16)),
    gasUsed = 0,
    unixTimestamp = timestamp,
    extraData = ByteVector.fromValidHex(extraData),
    mixHash = ByteVector.empty,
    nonce = ByteVector.fromValidHex(nonce)
  )

  lazy val body = BlockBody(Nil, Nil)

  lazy val block = Block(header, body)
}

object GenesisConfig {
  val default = GenesisConfig(
    nonce = "0x42",
    difficulty = "0x0400",
    extraData = "0x11bbe8db4e347b4e8c937c1c8370e4b5ed33adb3db69cbdb7a38e1e50b1b82fa",
    gasLimit = "0xff1388",
    coinbase = "0x0000000000000000000000000000000000000000",
    timestamp = System.currentTimeMillis(),
    alloc = Map(
      "d7a681378321f472adffb9fdded2712f677e0ba9" -> "1000000000000000000000000000000000000000000"
    ),
    chainId = 1
  )
}
