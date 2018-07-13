package jbok.core.models

import jbok.codec.codecs._
import jbok.codec._
import scodec.Codec
import scodec.bits.ByteVector

case class Address(bytes: ByteVector) extends AnyVal

object Address {
  implicit val codec: Codec[Address] = codecBytes.as[Address]

  implicit val je: io.circe.Encoder[Address] = encodeByteVector.contramap[Address](_.bytes)

  implicit val jd: io.circe.Decoder[Address] = decodeByteVector.map(Address.apply)

  val numBytes = 20

  def fromHex(hex: String): Address = Address(ByteVector.fromValidHex(hex))

  def apply(bytes: Array[Byte]): Address = Address(ByteVector(bytes))

  def apply(addr: Long): Address = Address(UInt256(addr))

  def apply(uint: UInt256): Address = Address(uint.bytes)
}
