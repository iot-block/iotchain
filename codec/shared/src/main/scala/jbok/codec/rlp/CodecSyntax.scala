package jbok.codec.rlp

import scodec.bits._

trait CodecSyntax {
  implicit def implicitCodecOps[A: RlpCodec](a: A): CodecOps[A]          = new CodecOps[A](a)
  implicit def implicitBitsDecodeOps(bits: BitVector): BitsDecodeOps     = new BitsDecodeOps(bits)
  implicit def implicitBytesDecodeOps(bytes: ByteVector): BytesDecodeOps = new BytesDecodeOps(bytes)
}

