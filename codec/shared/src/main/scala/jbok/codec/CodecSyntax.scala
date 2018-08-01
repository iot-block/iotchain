package jbok.codec

import java.nio.charset.StandardCharsets

import cats.effect.{IO, Sync}
import cats.implicits._
import io.circe.Json
import scodec.Encoder
import scodec.bits.{BitVector, ByteVector}

trait CodecSyntax {
  implicit final def codecSyntax[A](a: A): encodeOps[A] = new encodeOps[A](a)

  implicit final def stringCodecSyntax(s: String): StringCodecOps = new StringCodecOps(s)

  implicit final def bigIntSyntax(bi: BigInt): BigIntOps = new BigIntOps(bi)
}

final class encodeOps[A](val a: A) extends AnyVal {
  def bits[F[_]](implicit encoder: Encoder[A], F: Sync[F]): F[BitVector] =
    F.delay(encoder.encode(a).require)

  def bytes[F[_]](implicit encoder: Encoder[A], F: Sync[F]): F[ByteVector] =
    bits[F].map(_.bytes)

  def asBytes(implicit encoder: Encoder[A]): ByteVector = bytes[IO].unsafeRunSync()

  def asJson(implicit encoder: io.circe.Encoder[A]): Json = encoder(a)
}

final class StringCodecOps(val s: String) extends AnyVal {
  def decodeJson[A](implicit decoder: io.circe.Decoder[A]): Either[io.circe.Error, A] = io.circe.parser.decode[A](s)

  def utf8Bytes: ByteVector = ByteVector(s.getBytes(StandardCharsets.UTF_8))
}

final class BigIntOps(val bi: BigInt) extends AnyVal {
  def toUnsignedByteArray: Array[Byte] = {
    val asByteArray = bi.toByteArray
    if (asByteArray.head == 0) asByteArray.tail
    else asByteArray
  }
}
