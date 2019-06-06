package jbok.codec.json

import io.circe._
import io.circe.generic.extras._
import jbok.codec.rlp.RlpEncoded
import scodec.bits.ByteVector
import shapeless._
import spire.math.SafeLong

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Try

@SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
trait implicits {
  implicit val jsonConfig: Configuration = Configuration.default

  implicit val bytesJsonEncoder: Encoder[ByteVector] = Encoder.encodeString.contramap[ByteVector](_.toHex)
  implicit val bytesJsonDecoder: Decoder[ByteVector] = Decoder.decodeString.emap[ByteVector](ByteVector.fromHexDescriptive(_))

  implicit val rlpJsonEncoder: Encoder[RlpEncoded] = bytesJsonEncoder.contramap(_.bytes)
  implicit val rlpJsonDecoder: Decoder[RlpEncoded] = bytesJsonDecoder.map(bytes => RlpEncoded.coerce(bytes.bits))

  implicit val finiteDurationJsonEncoder: Encoder[FiniteDuration] = Encoder.encodeString.contramap[FiniteDuration](d => s"${d.length} ${d.unit.toString.toLowerCase}")
  implicit val finiteDurationJsonDecoder: Decoder[FiniteDuration] = Decoder.decodeString.emapTry[FiniteDuration](s => Try(Duration.apply(s).asInstanceOf[FiniteDuration]))

  implicit val bigIntJsonEncoder: Encoder[BigInt] = Encoder.encodeString.contramap[BigInt](_.toString(10))
  implicit val bigIntJsonDecoder: Decoder[BigInt] = Decoder.decodeString.emapTry[BigInt](s => Try(BigInt(s)))

  implicit val safeLongJsonEncoder: Encoder[SafeLong] = Encoder.encodeString.contramap[SafeLong](_.toString)
  implicit val safeLongJsonDecoder: Decoder[SafeLong] = Decoder.decodeString.emapTry[SafeLong](
    s =>
      if (s.startsWith("0x")) Try(SafeLong(BigInt(s.drop(2), 16)))
      else Try(SafeLong(BigInt(s)))
  )

  // key codecs
  implicit val bigIntKeyEncoder: KeyEncoder[BigInt] = KeyEncoder.encodeKeyString.contramap[BigInt](_.toString(10))
  implicit val bigIntKeyDecoder: KeyDecoder[BigInt] = KeyDecoder.decodeKeyString.map[BigInt](BigInt.apply)

  implicit val safeLongKeyEncoder: KeyEncoder[SafeLong] = KeyEncoder.encodeKeyString.contramap[SafeLong](_.toString)
  implicit val safeLongKeyDecoder: KeyDecoder[SafeLong] = KeyDecoder.decodeKeyString.map[SafeLong](s => SafeLong(BigInt(s)))

  // codec for value classes
  implicit def decoderJsonValueClass[T <: AnyVal, V](
      implicit
      g: Lazy[Generic.Aux[T, V :: HNil]],
      d: Decoder[V]
  ): Decoder[T] = Decoder.instance { cursor ⇒
    d(cursor).map { value ⇒
      g.value.from(value :: HNil)
    }
  }

  implicit def encoderJsonValueClass[T <: AnyVal, V](
      implicit
      g: Lazy[Generic.Aux[T, V :: HNil]],
      e: Encoder[V]
  ): Encoder[T] = Encoder.instance { value ⇒
    e(g.value.to(value).head)
  }
}

object implicits extends implicits
