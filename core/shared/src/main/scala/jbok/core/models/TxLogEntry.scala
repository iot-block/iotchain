package jbok.core.models

import scodec.bits.ByteVector
import jbok.codec.json.implicits._
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("TxLogEntry")
@JSExportAll
final case class TxLogEntry(loggerAddress: Address, logTopics: List[ByteVector], data: ByteVector)

object TxLogEntry {
  implicit val txLogEntryJsonEncoder = deriveEncoder[TxLogEntry]

  implicit val txLogEntryJsonDecoder = deriveDecoder[TxLogEntry]
}
