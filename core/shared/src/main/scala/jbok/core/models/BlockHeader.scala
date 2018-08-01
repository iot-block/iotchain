package jbok.core.models

import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.codecs._
import jbok.crypto._
import scodec.bits._
import shapeless._

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
  lazy val hash: ByteVector = RlpCodec.encode(this).require.bytes.kec256

  lazy val hashWithoutNonce: ByteVector = BlockHeader.encodeWithoutNonce(this).kec256
}

object BlockHeader {
  def encodeWithoutNonce(header: BlockHeader): ByteVector = {
    val hlist = Generic[BlockHeader].to(header).take(13)
    RlpCodec.encode(hlist).require.bytes
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
