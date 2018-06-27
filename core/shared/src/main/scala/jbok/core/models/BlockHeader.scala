package jbok.core.models

import scodec.Codec
import scodec.bits._
import scodec.codecs._

case class BlockHeader(
    parentHash: ByteVector,
    ommersHash: ByteVector,
    beneficiary: ByteVector,
    stateRoot: ByteVector,
    transactionsRoot: ByteVector,
    receiptsRoot: ByteVector,
    logsBloom: ByteVector,
    difficulty: BigInt,
    number: BigInt,
    gasLimit: BigInt,
    gasUsed: BigInt,
    unixTimestamp: Long,
    extraData: ByteVector,
    mixHash: ByteVector,
    nonce: ByteVector
) {
  val hash: ByteVector = ByteVector.fromHex("0xabcd").get
}

object BlockHeader {
  implicit val codec: Codec[BlockHeader] = {
    variableSizeBytes(uint8, bytes) ::
      variableSizeBytes(uint8, bytes) ::
      variableSizeBytes(uint8, bytes) ::
      variableSizeBytes(uint8, bytes) ::
      variableSizeBytes(uint8, bytes) ::
      variableSizeBytes(uint8, bytes) ::
      variableSizeBytes(uint8, bytes) ::
      Codec[BigInt] ::
      Codec[BigInt] ::
      Codec[BigInt] ::
      Codec[BigInt] ::
      int64 ::
      variableSizeBytes(uint8, bytes) ::
      variableSizeBytes(uint8, bytes) ::
      variableSizeBytes(uint8, bytes)
  }.as[BlockHeader]
}
