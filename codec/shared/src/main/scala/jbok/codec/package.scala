package jbok

package object codec {
  type Codec2[T, Payload] = Encoder[T, Payload] with Decoder[T, Payload]

  implicit class CodecOps[Type, Payload](private val codec: Codec2[Type, Payload]) extends AnyVal {
    def imap[T](f: Type => T)(g: T => Type): Codec2[T, Payload] = Codec2(codec.contramap(g), codec.map(f))
  }
}
