package jbok.core.sync

import jbok.codec.rlp.RlpCodec
import scodec.bits.ByteVector
import scodec.codecs.{bytes, discriminated, uint8}

sealed trait NodeHash {
  def v: ByteVector
}

object NodeHash {
  case class StateMptNodeHash(v: ByteVector)   extends NodeHash
  case class StorageMptNodeHash(v: ByteVector) extends NodeHash
  case class EvmCodeHash(v: ByteVector)        extends NodeHash

  implicit val codec: RlpCodec[NodeHash] = RlpCodec.item(
    discriminated[NodeHash]
      .by(uint8)
      .subcaseO(1) {
        case t: StateMptNodeHash => Some(t)
        case _                   => None
      }(bytes.as[StateMptNodeHash])
      .subcaseO(2) {
        case t: StorageMptNodeHash => Some(t)
        case _                     => None
      }(bytes.as[StorageMptNodeHash])
      .subcaseO(3) {
        case t: EvmCodeHash => Some(t)
        case _              => None
      }(bytes.as[EvmCodeHash])
  )
}
