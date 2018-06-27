package jbok.core.models

import scodec.bits._

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
  val hash: ByteVector = ???
}
