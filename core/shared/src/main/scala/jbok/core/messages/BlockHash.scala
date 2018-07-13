package jbok.core.messages

import scodec.Codec
import scodec.bits.ByteVector
import jbok.codec.codecs._

case class BlockHash(hash: ByteVector, number: BigInt) extends Message
object BlockHash {
  implicit val codec: Codec[BlockHash] = (codecBytes :: codecBigInt).as[BlockHash]
}

case class NewBlockHashes(hashes: List[BlockHash]) extends Message
object NewBlockHashes {
  implicit val codec: Codec[NewBlockHashes] = codecList[BlockHash].as[NewBlockHashes]
}
