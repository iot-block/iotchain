package jbok.core.models

import scodec.Codec
import scodec.bits._
import jbok.codec.codecs._

case class Receipt(
    postTransactionStateHash: ByteVector,
    cumulativeGasUsed: BigInt,
    logsBloomFilter: ByteVector,
    logs: List[TxLogEntry]
)

object Receipt {
  implicit val codec: Codec[Receipt] = {
    codecBytes ::
      codecBigInt ::
      codecBytes ::
      codecList[TxLogEntry]
  }.as[Receipt]
}
