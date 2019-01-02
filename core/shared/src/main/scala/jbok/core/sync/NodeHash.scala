package jbok.core.sync

import scodec.bits.ByteVector

sealed trait NodeHash {
  def v: ByteVector
}

object NodeHash {
  final case class StateMptNodeHash(v: ByteVector)   extends NodeHash
  final case class StorageMptNodeHash(v: ByteVector) extends NodeHash
  final case class EvmCodeHash(v: ByteVector)        extends NodeHash
}
