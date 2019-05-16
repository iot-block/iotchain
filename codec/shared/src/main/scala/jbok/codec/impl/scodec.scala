package jbok.codec.impl

import java.nio.ByteBuffer

import _root_.scodec.bits.BitVector
import jbok.codec._

object scodec {
  final case class DeserializeException(msg: String) extends Exception(msg)

  implicit def scodecCodec[T: _root_.scodec.Codec]: Codec2[T, ByteBuffer] =
    new Encoder[T, ByteBuffer] with Decoder[T, ByteBuffer] {
      override def encode(arg: T): ByteBuffer = _root_.scodec.Codec[T].encode(arg).require.toByteBuffer
      override def decode(arg: ByteBuffer): Either[Throwable, T] =
        _root_.scodec.Codec[T].decode(BitVector(arg)).toEither match {
          case Right(result) => Right(result.value)
          case Left(err)     => Left(DeserializeException(err.messageWithContext))
        }
    }
}
