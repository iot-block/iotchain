package jbok.core.models

import jbok.codec.json.{bytesDecoder, bytesEncoder}
import jbok.codec.rlp.RlpCodec
import jbok.codec.json._
import jbok.codec.rlp.codecs._
import jbok.crypto._
import jbok.crypto.signature.KeyPair
import scodec.bits.ByteVector

class Address private (val bytes: ByteVector) extends AnyVal {
  def toUInt256: UInt256 = UInt256(bytes)

  override def toString: String = s"0x${bytes.toHex}"
}

object Address {
  val numBytes = 20

  implicit val codec: RlpCodec[Address] = rbytes.xmap[Address](Address.apply, _.bytes)

  implicit val je: io.circe.Encoder[Address] = bytesEncoder.contramap[Address](_.bytes)

  implicit val jd: io.circe.Decoder[Address] = bytesDecoder.map(Address.apply)

  def fromHex(hex: String): Address = Address.apply(ByteVector.fromValidHex(hex))

  def apply(bytes: Array[Byte]): Address = Address.apply(ByteVector(bytes))

  def apply(addr: Long): Address = Address.apply(UInt256(addr))

  def apply(uint: UInt256): Address = Address.apply(uint.bytes)

  def apply(bytes: ByteVector): Address = new Address(bytes.takeRight(numBytes).padLeft(numBytes))

  def apply(keyPair: KeyPair): Address = Address(keyPair.public.bytes.kec256)

  def empty: Address = Address(ByteVector.empty)
}
