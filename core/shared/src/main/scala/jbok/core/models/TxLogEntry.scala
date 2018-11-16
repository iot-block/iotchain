package jbok.core.models

import scodec.bits.ByteVector

final case class TxLogEntry(loggerAddress: Address, logTopics: List[ByteVector], data: ByteVector)
