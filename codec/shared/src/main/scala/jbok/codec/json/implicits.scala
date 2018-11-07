package jbok.codec.json

import io.circe._
import io.circe.generic.AutoDerivation
import scodec.bits.{BitVector, ByteVector}
import io.circe.syntax._
import shapeless._

import scala.concurrent.duration.{Duration, FiniteDuration}

object implicits extends AutoDerivation {
  implicit val bytesDecoder: Decoder[ByteVector] = Decoder[String].emap(ByteVector.fromHexDescriptive(_))

  implicit val bytesEncoder: Encoder[ByteVector] = Encoder.instance(bv => Json.fromString(bv.toHex))

  implicit val bigIntDecoder: Decoder[BigInt] = Decoder[String].map[BigInt](
    x =>
      if (x.startsWith("0x"))
        BigInt(x.substring(2, x.length), 16)
      else
        BigInt(x))

  implicit val bigIntEncoder: Encoder[BigInt] = Encoder[String].contramap[BigInt](_.toString(10))

  implicit val durationEncoder: Encoder[Duration] = Encoder.instance[Duration](d => s"${d.length}${d.unit}".asJson)

  implicit val durationDecoder: Decoder[Duration] = Decoder[String].map(s => Duration.apply(s))

  implicit val finiteDurationEncoder: Encoder[FiniteDuration] =
    Encoder.instance[FiniteDuration](d => s"${d.length}${d.unit.toString.toLowerCase}".asJson)

  implicit val finiteDurationDecoder: Decoder[FiniteDuration] =
    Decoder[String].map(s => Duration.apply(s).asInstanceOf[FiniteDuration])

  implicit final val decodeBitVector: Decoder[BitVector] = decodeBitVectorWithNames("bits", "length")

  implicit final val encodeBitVector: Encoder[BitVector] = encodeBitVectorWithNames("bits", "length")

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

  private def decodeBitVectorWithNames(bitsName: String, lengthName: String): Decoder[BitVector] =
    Decoder.instance { c =>
      val bits: Decoder.Result[BitVector] = c.get[String](bitsName).right.flatMap { bs =>
        BitVector.fromBase64Descriptive(bs) match {
          case r @ Right(_)  => r.asInstanceOf[Decoder.Result[BitVector]]
          case Left(message) => Left(DecodingFailure(message, c.history))
        }
      }

      Decoder.resultInstance.map2(bits, c.get[Long](lengthName))(_.take(_))
    }

  private def encodeBitVectorWithNames(bitsName: String, lengthName: String): ObjectEncoder[BitVector] =
    ObjectEncoder.instance { bv =>
      JsonObject
        .singleton(bitsName, Json.fromString(bv.toBase64))
        .add(
          lengthName,
          Json.fromLong(bv.size)
        )
    }
}
