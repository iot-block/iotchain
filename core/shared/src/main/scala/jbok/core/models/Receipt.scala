package jbok.core.models

import jbok.codec.json.implicits._
import scodec.bits._
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("Receipt")
@JSExportAll
final case class Receipt(
    postTransactionStateHash: ByteVector,
    cumulativeGasUsed: BigInt,
    logsBloomFilter: ByteVector,
    logs: List[TxLogEntry],
    txHash: ByteVector,
    contractAddress: Option[Address],
    gasUsed: BigInt
)

object Receipt {
  implicit val receiptJsonEncoder = deriveEncoder[Receipt]

  implicit val receiptJsonDecoder = deriveDecoder[Receipt]
}
