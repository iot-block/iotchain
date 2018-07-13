package jbok.core.models

import scodec.Codec
import scodec.bits._
import jbok.codec.codecs._
import jbok.crypto._

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
  val hash: ByteVector = Codec[BlockHeader].encode(this).require.bytes.kec256
}

object BlockHeader {
  implicit val codec: Codec[BlockHeader] = {
    codecBytes ::
      codecBytes ::
      codecBytes ::
      codecBytes ::
      codecBytes ::
      codecBytes ::
      codecBytes ::
      codecBigInt ::
      codecBigInt ::
      codecBigInt ::
      codecBigInt ::
      codecLong ::
      codecBytes ::
      codecBytes ::
      codecBytes
  }.as[BlockHeader]

  def empty: BlockHeader = BlockHeader(
    ByteVector.empty,
    ByteVector.empty,
    ByteVector.empty,
    ByteVector.empty,
    ByteVector.empty,
    ByteVector.empty,
    ByteVector.empty,
    BigInt(0),
    BigInt(0),
    BigInt(0),
    BigInt(0),
    0L,
    ByteVector.empty,
    ByteVector.empty,
    ByteVector.empty
  )
}
