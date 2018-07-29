package jbok.core.models

import scodec.{Attempt, Codec, Encoder, SizeBound}
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

  val hashWithoutNonce: ByteVector = BlockHeader.codecWithoutNonce.encode(this).require.bytes.kec256
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

  val codecWithoutNonce: Encoder[BlockHeader] = new Encoder[BlockHeader] {
    val codec = codecBytes ::
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
      codecBytes
    override def encode(h: BlockHeader): Attempt[BitVector] = {
      import shapeless._
      val hlist = h.parentHash ::
        h.ommersHash ::
        h.beneficiary ::
        h.stateRoot ::
        h.transactionsRoot ::
        h.receiptsRoot ::
        h.logsBloom ::
        h.difficulty ::
        h.number ::
        h.gasLimit ::
        h.gasUsed ::
        h.unixTimestamp ::
        h.extraData :: HNil
      codec.encode(hlist)
    }

    override def sizeBound: SizeBound = SizeBound.unknown
  }

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
