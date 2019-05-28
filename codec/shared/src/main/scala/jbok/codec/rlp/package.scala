package jbok.codec

package object rlp {
  object implicits extends RlpCodecInstances with RlpCodecSyntax {
    type RlpCodec[A] = jbok.codec.rlp.RlpCodec[A]

    val RlpCodec = jbok.codec.rlp.RlpCodec
  }
}
