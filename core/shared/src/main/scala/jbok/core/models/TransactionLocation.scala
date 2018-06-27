package jbok.core.models

import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs._

case class TransactionLocation(blockHash: ByteVector, txIndex: Int)
object TransactionLocation {
  implicit val codec: Codec[TransactionLocation] = (variableSizeBytes(uint8, bytes) :: uint16).as[TransactionLocation]
}
