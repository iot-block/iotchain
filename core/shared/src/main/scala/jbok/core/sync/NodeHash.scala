package jbok.core.sync

import jbok.codec.rlp.implicits._
import scodec.bits.ByteVector

sealed trait NodeHash {
  def v: ByteVector
}

object NodeHash {
  case class StateMptNodeHash(v: ByteVector)   extends NodeHash
  case class StorageMptNodeHash(v: ByteVector) extends NodeHash
  case class EvmCodeHash(v: ByteVector)        extends NodeHash

  implicit val codec = RlpCodec[NodeHash]
}
