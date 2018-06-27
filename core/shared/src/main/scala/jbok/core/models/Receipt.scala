package jbok.core.models

import scodec.Codec
import scodec.bits._
import scodec.codecs._
case class Receipt(
    postTransactionStateHash: ByteVector,
    cumulativeGasUsed: BigInt,
    logsBloomFilter: ByteVector,
    logs: List[TxLogEntry]
)

object Receipt {
  implicit val codec: Codec[Receipt] = {
    variableSizeBytes(uint8, bytes) ::
    Codec[BigInt] ::
    variableSizeBytes(uint8, bytes) ::
    list(Codec[TxLogEntry])
  }.as[Receipt]
}
