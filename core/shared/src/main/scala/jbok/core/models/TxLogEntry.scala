package jbok.core.models

import io.circe.generic.JsonCodec
import scodec.bits.ByteVector
import jbok.codec.json.implicits._

@JsonCodec
final case class TxLogEntry(loggerAddress: Address, logTopics: List[ByteVector], data: ByteVector)
