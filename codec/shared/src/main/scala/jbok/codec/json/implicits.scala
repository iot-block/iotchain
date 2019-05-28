package jbok.codec.json

import io.circe._
import scodec.bits.ByteVector
import shapeless._
import io.circe.generic.extras._

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Try

@SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
object implicits {
  implicit val config: Configuration = Configuration.default

  implicit val bytesEncoder: Encoder[ByteVector] = Encoder.encodeString.contramap[ByteVector](_.toHex)
  implicit val bytesDecoder: Decoder[ByteVector] = Decoder.decodeString.emap[ByteVector](ByteVector.fromHexDescriptive(_))

  implicit val finiteDurationEncoder: Encoder[FiniteDuration] = Encoder.encodeString.contramap[FiniteDuration](d => s"${d.length} ${d.unit.toString.toLowerCase}")
  implicit val finiteDurationDecoder: Decoder[FiniteDuration] = Decoder.decodeString.emapTry[FiniteDuration](s => Try(Duration.apply(s).asInstanceOf[FiniteDuration]))

  implicit val bigIntEncoder: Encoder[BigInt] = Encoder.encodeString.contramap[BigInt](_.toString(10))
  implicit val bigIntDecoder: Decoder[BigInt] = Decoder.decodeString.emapTry[BigInt](s => Try(BigInt.apply(s)))

  // key codecs
  implicit val bigIntKeyEncoder: KeyEncoder[BigInt] = KeyEncoder.encodeKeyString.contramap[BigInt](_.toString(10))
  implicit val bigIntKeyDecoder: KeyDecoder[BigInt] = KeyDecoder.decodeKeyString.map[BigInt](BigInt.apply)

  // codec for value classes
  implicit def decoderValueClass[T <: AnyVal, V](
      implicit
      g: Lazy[Generic.Aux[T, V :: HNil]],
      d: Decoder[V]
  ): Decoder[T] = Decoder.instance { cursor ⇒
    d(cursor).map { value ⇒
      g.value.from(value :: HNil)
    }
  }

  implicit def encoderValueClass[T <: AnyVal, V](
      implicit
      g: Lazy[Generic.Aux[T, V :: HNil]],
      e: Encoder[V]
  ): Encoder[T] = Encoder.instance { value ⇒
    e(g.value.to(value).head)
  }
}
