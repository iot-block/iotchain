package jbok.core.models

import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.codecs._
import scodec.bits.ByteVector

case class TxLogEntry(loggerAddress: Address, logTopics: List[ByteVector], data: ByteVector)
