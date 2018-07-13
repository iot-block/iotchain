package jbok.codec

import io.circe._
import scodec.bits.ByteVector

package object json {
  implicit val bytesDecoder: Decoder[ByteVector] = Decoder[String].emap(ByteVector.fromBase64Descriptive(_))
  implicit val bytesEncoder: Encoder[ByteVector] = Encoder.instance(bv => Json.fromString(bv.toBase64))

  implicit val bigIntDecoder: Decoder[BigInt] = Decoder[String].map[BigInt](BigInt.apply)
  implicit val bigIntEncoder: Encoder[BigInt] = Encoder[String].contramap[BigInt](_.toString(10))
}
