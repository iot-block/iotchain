package jbok.codec.rlp

final class RlpEncodeOps[A](val a: A) extends AnyVal {
  def encoded(implicit codec: RlpCodec[A]): RlpEncoded =
    RlpEncoded.coerce(codec.encode(a).require)
}
