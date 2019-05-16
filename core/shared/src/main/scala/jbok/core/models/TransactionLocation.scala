package jbok.core.models

import io.circe.generic.JsonCodec
import scodec.bits.ByteVector
import jbok.codec.json.implicits._

@JsonCodec
final case class TransactionLocation(blockHash: ByteVector, txIndex: Int)
