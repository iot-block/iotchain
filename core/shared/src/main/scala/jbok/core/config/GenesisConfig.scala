package jbok.core.config

import better.files.File
import cats.effect.IO
import io.circe.Json
import io.circe.generic.JsonCodec
import io.circe.parser._
import io.circe.syntax._
import jbok.codec.json.implicits._
import jbok.core.models._
import jbok.crypto.authds.mpt.MerklePatriciaTrie
import scodec.bits._

@JsonCodec
final case class GenesisConfig(
    nonce: ByteVector,
    difficulty: BigInt,
    extraData: ByteVector,
    gasLimit: BigInt,
    coinbase: ByteVector,
    alloc: Map[String, String],
    chainId: BigInt,
    timestamp: Long
) {
  def json: Json = this.asJson

  def withAlloc(alloc: Map[Address, BigInt]): GenesisConfig =
    copy(alloc = alloc.map { case (k, v) => k.toString -> v.toString() })

  lazy val header = BlockHeader(
    parentHash = ByteVector.empty,
    ommersHash = ByteVector.empty,
    beneficiary = coinbase,
    stateRoot = MerklePatriciaTrie.emptyRootHash,
    transactionsRoot = MerklePatriciaTrie.emptyRootHash,
    receiptsRoot = MerklePatriciaTrie.emptyRootHash,
    logsBloom = ByteVector.empty,
    difficulty = difficulty,
    number = 0,
    gasLimit = gasLimit,
    gasUsed = 0,
    unixTimestamp = timestamp,
    extra = extraData
  )

  lazy val body = BlockBody(Nil, Nil)

  lazy val block = Block(header, body)
}

object GenesisConfig {
  def generate(chainId: BigInt, alloc: Map[Address, BigInt]): GenesisConfig =
    GenesisConfig(
      nonce = hex"0x42",
      difficulty = BigInt("1024"),
      extraData = hex"",
      gasLimit = BigInt("16716680"),
      coinbase = hex"0x0000000000000000000000000000000000000000",
      alloc = alloc.map { case (key, value) => key.toString -> value.toString },
      chainId = chainId,
      timestamp = 0
    )

  def fromFile(path: String): IO[GenesisConfig] =
    IO(File(path).lines.mkString("\n")).map(text => decode[GenesisConfig](text) match {
      case Left(e) => throw new Exception(s"read genesis file from ${path} error, ${e}")
      case Right(c) => c
    })
}
