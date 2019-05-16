package jbok.codec

object Codec2 {
  def apply[A, P](implicit encoder: Encoder[A, P], decoder: Decoder[A, P]): Codec2[A, P] =
    new Encoder[A, P] with Decoder[A, P] {
      def encode(arg: A): P                    = encoder.encode(arg)
      def decode(arg: P): Either[Throwable, A] = decoder.decode(arg)
    }
}
