package jbok.codec.rlp

import scodec._
import scodec.bits.BitVector

// we are cheating here...
// do not use methods other than encode & decode
trait RlpCodec[A] extends Codec[A] {
  override def sizeBound: SizeBound = SizeBound.unknown
}

object RlpCodec extends RlpCodecInstances {
  def apply[A](implicit ev: RlpCodec[A]): RlpCodec[A] = ev

  def isItemPrefix(bits: BitVector): Boolean =
    bits.bytes.headOption match {
      case Some(byte) => (0xff & byte) < RlpCodecHelper.listOffset
      case None       => false
    }

  def isEmptyPrefix(bits: BitVector): Boolean =
    bits.bytes.headOption match {
      case Some(byte) => byte == RlpCodecHelper.itemOffset.toByte
      case None       => false
    }
}
