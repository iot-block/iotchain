package jbok

import io.circe._
import scodec.bits.{BitVector, ByteVector}
import shapeless._

package object codec extends CodecSyntax {
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
          case r @ Right(_) => r.asInstanceOf[Decoder.Result[BitVector]]
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
