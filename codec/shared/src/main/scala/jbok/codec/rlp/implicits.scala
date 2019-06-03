package jbok.codec.rlp

trait implicits extends RlpCodecInstances with RlpCodecSyntax
object implicits extends implicits {
  type RlpCodec[A] = jbok.codec.rlp.RlpCodec[A]

  val RlpCodec = jbok.codec.rlp.RlpCodec
}
