package jbok.core.models

import io.circe.generic.JsonCodec
import jbok.codec.json.implicits._
import scodec.bits._

import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("Receipt")
@JSExportAll
@JsonCodec
final case class Receipt(
    postTransactionStateHash: ByteVector,
    cumulativeGasUsed: BigInt,
    logsBloomFilter: ByteVector,
    logs: List[TxLogEntry],
    txHash: ByteVector,
    contractAddress: Option[Address],
    gasUsed: BigInt
)
