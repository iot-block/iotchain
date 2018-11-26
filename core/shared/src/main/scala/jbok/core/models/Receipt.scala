package jbok.core.models

import io.circe.generic.JsonCodec
import jbok.codec.json.implicits._
import scodec.bits._

@JsonCodec
final case class Receipt(
    postTransactionStateHash: ByteVector,
    cumulativeGasUsed: BigInt,
    logsBloomFilter: ByteVector,
    logs: List[TxLogEntry]
)


