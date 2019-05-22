package jbok.codec.json

import io.circe._
import io.circe.syntax._
import scodec.bits.ByteVector
import shapeless._

import scala.concurrent.duration.{Duration, FiniteDuration}

@SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
object implicits {
  implicit val bytesDecoder: Decoder[ByteVector] = Decoder[String].emap(ByteVector.fromHexDescriptive(_))

  implicit val bytesEncoder: Encoder[ByteVector] = Encoder.instance(bv => Json.fromString(bv.toHex))

  implicit val durationEncoder: Encoder[Duration] = Encoder.instance[Duration](d => s"${d.length}${d.unit}".asJson)

  implicit val durationDecoder: Decoder[Duration] = Decoder[String].map(s => Duration.apply(s))

  implicit val finiteDurationEncoder: Encoder[FiniteDuration] =
    Encoder.instance[FiniteDuration](d => s"${d.length} ${d.unit.toString.toLowerCase}".asJson)

  implicit val finiteDurationDecoder: Decoder[FiniteDuration] =
    Decoder[String].map(s => Duration.apply(s).asInstanceOf[FiniteDuration])

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
