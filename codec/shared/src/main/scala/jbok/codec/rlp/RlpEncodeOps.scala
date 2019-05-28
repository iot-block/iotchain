package jbok.codec.rlp

import cats.implicits._
import scodec.bits.ByteVector

final class RlpEncodeOps[A](val a: A) extends AnyVal {
  def asBytes(implicit codec: RlpCodec[A]): ByteVector = codec.encode(a).require.bytes
}

final class RlpDecodeOps(val bytes: ByteVector) extends AnyVal {
  def asOpt[A](implicit codec: RlpCodec[A]): Option[A] =
    codec.decode(bytes.bits).map(_.value).toOption

  def asEither[A](implicit codec: RlpCodec[A]): Either[Throwable, A] =
    codec.decode(bytes.bits).toEither.leftMap(err => new Exception(err.messageWithContext)).map(_.value)
}
