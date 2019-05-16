package jbok.codec

trait Encoder[A, P] { self =>
  def encode(arg: A): P

  final def contramap[B](f: B => A): Encoder[B, P] = new Encoder[B, P] {
    def encode(arg: B): P = self.encode(f(arg))
  }
}

object Encoder {
  def apply[A, P](implicit ev: Encoder[A, P]): Encoder[A, P] = ev
}
