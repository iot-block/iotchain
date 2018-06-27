package jbok.core.models

import scodec.Codec
import scodec.codecs._
import scodec.bits.ByteVector

case class Address(bytes: ByteVector) extends AnyVal

object Address {
  implicit val codec: Codec[Address] = bytes.as[Address]
}
