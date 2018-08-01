package jbok.core.models

import jbok.codec.rlp.RlpCodec
import scodec.bits.ByteVector

case class TransactionLocation(blockHash: ByteVector, txIndex: Int)
object TransactionLocation {
  implicit val codec: RlpCodec[TransactionLocation] = RlpCodec[TransactionLocation]
}
