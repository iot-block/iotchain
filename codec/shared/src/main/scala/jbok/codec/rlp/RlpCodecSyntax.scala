package jbok.codec.rlp

import scodec.bits._

trait RlpCodecSyntax {
  implicit def implicitRlpEncodeOps[A: RlpCodec](a: A): RlpEncodeOps[A] = new RlpEncodeOps[A](a)
  implicit def implicitRlpDecodeOps(bytes: ByteVector): RlpDecodeOps    = new RlpDecodeOps(bytes)
}
