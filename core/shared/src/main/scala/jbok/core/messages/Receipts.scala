package jbok.core.messages

import jbok.core.models.Receipt
import scodec.Codec
import scodec.bits.ByteVector
import jbok.codec.codecs._

case class GetReceipts(blockHashes: List[ByteVector]) extends Message
object GetReceipts {
  implicit val codec: Codec[GetReceipts] = codecList[ByteVector].as[GetReceipts]
}

case class Receipts(receiptsForBlocks: List[List[Receipt]]) extends Message
object Receipts {
  implicit val codec: Codec[Receipts] = codecList[List[Receipt]].as[Receipts]
}
