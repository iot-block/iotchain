package jbok.core.models

import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs._

case class TxLogEntry(loggerAddress: Address, logTopics: List[ByteVector], data: ByteVector)
object TxLogEntry {
  implicit val codec: Codec[TxLogEntry] = {
    Codec[Address] ::
    list(variableSizeBytes(uint8, bytes)) ::
    variableSizeBytes(uint8, bytes)
  }.as[TxLogEntry]
}
