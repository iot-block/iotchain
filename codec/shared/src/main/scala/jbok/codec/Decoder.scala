package jbok.codec

trait Decoder[A, P] { self =>
  def decode(arg: P): Either[Throwable, A]

  final def map[B](f: A => B): Decoder[B, P] = new Decoder[B, P] {
    def decode(arg: P): Either[Throwable, B] = self.decode(arg).right.map(f)
  }

  final def flatMap[B](f: A => Either[Throwable, B]): Decoder[B, P] = new Decoder[B, P] {
    def decode(arg: P): Either[Throwable, B] = self.decode(arg).right.flatMap(f)
  }
}

object Decoder {
  def apply[Type, Payload](implicit ev: Decoder[Type, Payload]): Decoder[Type, Payload] = ev
}
