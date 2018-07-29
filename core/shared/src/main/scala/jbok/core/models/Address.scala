package jbok.core.models

import jbok.codec.codecs._
import jbok.codec.json.{bytesDecoder, bytesEncoder}
import jbok.crypto._
import jbok.crypto.signature.KeyPair
import pureconfig.ConfigReader
import scodec.Codec
import scodec.bits.ByteVector

class Address private (val bytes: ByteVector) extends AnyVal {
  def toUInt256: UInt256 = UInt256(bytes)

  override def toString: String = s"0x${bytes.toHex}"
}

object Address {
  val numBytes = 20

  implicit val codec: Codec[Address] = codecBytes.as[Address]

  implicit val je: io.circe.Encoder[Address] = bytesEncoder.contramap[Address](_.bytes)

  implicit val jd: io.circe.Decoder[Address] = bytesDecoder.map(Address.apply)

  implicit val reader: ConfigReader[Address] = ConfigReader[String].map(x => Address.fromHex(x))

  def fromHex(hex: String): Address = Address.apply(ByteVector.fromValidHex(hex))

  def apply(bytes: Array[Byte]): Address = Address.apply(ByteVector(bytes))

  def apply(addr: Long): Address = Address.apply(UInt256(addr))

  def apply(uint: UInt256): Address = Address.apply(uint.bytes)

  def apply(bytes: ByteVector): Address = new Address(bytes.takeRight(numBytes).padLeft(numBytes))

  def apply(keyPair: KeyPair): Address = Address(keyPair.public.uncompressed.kec256)
}
