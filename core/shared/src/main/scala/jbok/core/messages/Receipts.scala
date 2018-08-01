package jbok.core.messages

import jbok.core.models.Receipt
import scodec.bits.ByteVector

case class GetReceipts(blockHashes: List[ByteVector]) extends Message
case class Receipts(receiptsForBlocks: List[List[Receipt]]) extends Message
