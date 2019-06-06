package jbok.codec.rlp

trait RlpCodecSyntax {
  implicit final def implicitRlpEncodeOps[A: RlpCodec](a: A): RlpEncodeOps[A] = new RlpEncodeOps[A](a)
}
