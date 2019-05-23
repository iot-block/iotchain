package jbok.codec.rlp

import cats.effect.Sync
import cats.implicits._
import scodec.bits.{BitVector, ByteVector}

final class CodecOps[A](val a: A) extends AnyVal {
  def asValidBytes(implicit codec: RlpCodec[A]): ByteVector           = codec.encode(a).require.bytes
  def asBytes[F[_]: Sync](implicit codec: RlpCodec[A]): F[ByteVector] = Sync[F].delay(asValidBytes)
}

final class BitsDecodeOps(val bits: BitVector) extends AnyVal {
  def asOpt[A](implicit codec: RlpCodec[A]): Option[A] = codec.decode(bits).map(_.value).toOption
  def asEither[A](implicit codec: RlpCodec[A]): Either[Throwable, A] =
    codec.decode(bits).toEither.leftMap(err => new Exception(err.messageWithContext)).map(_.value)
}

final class BytesDecodeOps(val bytes: ByteVector) extends AnyVal {
  def asOpt[A](implicit codec: RlpCodec[A]): Option[A] = codec.decode(bytes.bits).map(_.value).toOption
  def asEither[A](implicit codec: RlpCodec[A]): Either[Throwable, A] =
    codec.decode(bytes.bits).toEither.leftMap(err => new Exception(err.messageWithContext)).map(_.value)
}
