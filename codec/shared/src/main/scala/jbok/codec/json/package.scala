package jbok.codec

import io.circe._
import scodec.bits.ByteVector

import scala.concurrent.duration.{Duration, FiniteDuration}

package object json {
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
}
