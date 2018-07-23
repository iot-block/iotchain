package jbok.core.models

import jbok.codec._
import jbok.codec.codecs._
import jbok.crypto._
import jbok.crypto.signature.KeyPair
import scodec.Codec
import scodec.bits.ByteVector

class Address private (val bytes: ByteVector) extends AnyVal {
  def toUInt256: UInt256 = UInt256(bytes)

  override def toString: String = s"0x${bytes.toHex}"
}

object Address {
  val numBytes = 20

  implicit val codec: Codec[Address] = codecBytes.as[Address]

  implicit val je: io.circe.Encoder[Address] = encodeByteVector.contramap[Address](_.bytes)

  implicit val jd: io.circe.Decoder[Address] = decodeByteVector.map(Address.apply)

  def fromHex(hex: String): Address = Address.apply(ByteVector.fromValidHex(hex))

  def apply(bytes: Array[Byte]): Address = Address.apply(ByteVector(bytes))

  def apply(addr: Long): Address = Address.apply(UInt256(addr))

  def apply(uint: UInt256): Address = Address.apply(uint.bytes)

  def apply(keyPair: KeyPair): Address = Address.apply(keyPair.public.value.kec256)

  def apply(bytes: ByteVector): Address = new Address(bytes.takeRight(numBytes).padLeft(numBytes))
}
