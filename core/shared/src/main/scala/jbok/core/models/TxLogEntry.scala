package jbok.core.models

import scodec.bits.ByteVector

case class TxLogEntry(loggerAddress: Address, logTopics: Seq[ByteVector], data: ByteVector)
