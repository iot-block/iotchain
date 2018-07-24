package jbok.core.models

import scodec.Codec
import scodec.bits.ByteVector
import jbok.codec.codecs._
import jbok.codec.rlp

case class TxLogEntry(loggerAddress: Address, logTopics: List[ByteVector], data: ByteVector)

object TxLogEntry {
  implicit val codec: Codec[TxLogEntry] = rlp.rstruct {
    {
      Codec[Address] ::
        codecList[ByteVector] ::
        codecBytes
    }.as[TxLogEntry]
  }
}
