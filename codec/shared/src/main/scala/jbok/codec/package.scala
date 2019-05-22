package jbok

package object codec {
  type Codec2[A, P] = Encoder[A, P] with Decoder[A, P]

  implicit class CodecOps[A, P](private val codec: Codec2[A, P]) extends AnyVal {
    def imap[B](f: A => B)(g: B => A): Codec2[B, P] = Codec2(codec.contramap(g), codec.map(f))
  }
}
