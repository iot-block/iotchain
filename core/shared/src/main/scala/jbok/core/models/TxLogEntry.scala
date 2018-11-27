package jbok.core.models

import scodec.bits.ByteVector
import jbok.codec.json.implicits._

final case class TxLogEntry(loggerAddress: Address, logTopics: List[ByteVector], data: ByteVector)

object TxLogEntry {
  implicit val txLogEntryJsonEncoder = deriveEncoder[TxLogEntry]

  implicit val txLogEntryJsonDecoder= deriveDecoder[TxLogEntry]
}
