package jbok.core.models

import scodec.Codec
import scodec.bits.ByteVector
import jbok.codec.codecs._

case class TxLogEntry(loggerAddress: Address, logTopics: List[ByteVector], data: ByteVector)
object TxLogEntry {
  implicit val codec: Codec[TxLogEntry] = {
    Codec[Address] ::
    codecList[ByteVector] ::
    codecBytes
  }.as[TxLogEntry]
}
