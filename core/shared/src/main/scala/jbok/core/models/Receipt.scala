package jbok.core.models

import jbok.codec.json.implicits._
import scodec.bits._

final case class Receipt(
    postTransactionStateHash: ByteVector,
    cumulativeGasUsed: BigInt,
    logsBloomFilter: ByteVector,
    logs: List[TxLogEntry]
)

object Receipt {
  implicit val receiptJsonEncoder = deriveEncoder[Receipt]

  implicit val receiptJsonDecoder = deriveDecoder[Receipt]
}
