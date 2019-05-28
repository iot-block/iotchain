package jbok.core.models

import io.circe.generic.extras.ConfiguredJsonCodec
import scodec.bits.ByteVector
import jbok.codec.json.implicits._

@ConfiguredJsonCodec
final case class TransactionLocation(blockHash: ByteVector, txIndex: Int)
