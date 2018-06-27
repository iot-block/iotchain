package jbok.core

import java.nio.charset.StandardCharsets

import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs._

package object models {
  implicit val codecString: Codec[String] = string(StandardCharsets.UTF_8)

//  implicit val codecBigInt: Codec[BigInt] = int64.xmap[BigInt](BigInt.apply, _.toLong)
  implicit val codecBigInt: Codec[BigInt] = variableSizeBytes(uint8, Codec[String]).xmap[BigInt](
    BigInt.apply, _.toString(10)
  )

  implicit val codecBV: Codec[ByteVector] = bytes

  implicit def codecList[A: Codec]: Codec[List[A]] = list(implicitly[Codec[A]])

  implicit val codecBoolean: Codec[Boolean] = bool
}
