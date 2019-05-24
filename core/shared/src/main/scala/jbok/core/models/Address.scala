package jbok.core.models

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.crypto._
import jbok.crypto.signature.KeyPair
import scodec.codecs
import scodec.bits.ByteVector

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

final class Address private (val bytes: ByteVector) extends AnyVal {
  @JSExport
  def toUInt256: UInt256 = UInt256(bytes)

  @JSExport
  override def toString: String = s"0x${bytes.toHex}"
}

@JSExportTopLevel("Address")
object Address {
  val numBytes = 20

  val empty: Address = Address(ByteVector.empty)

  implicit val codec: RlpCodec[Address] = rlp(codecs.bytes.xmap[Address](Address.apply, _.bytes))

  implicit val addressEncoder: Encoder[Address] = Encoder.encodeString.contramap[Address](_.toString)

  implicit val addressDecoder: Decoder[Address] = Decoder.decodeString.map(Address.fromHex)

  implicit val addressKeyEncoder: KeyEncoder[Address] = KeyEncoder.encodeKeyString.contramap[Address](_.toString)

  implicit val addressKeyDecoder: KeyDecoder[Address] = KeyDecoder.decodeKeyString.map[Address](Address.fromHex)

  @JSExport("fromString")
  def fromHex(hex: String): Address = Address.apply(ByteVector.fromValidHex(hex))

  def apply(bytes: Array[Byte]): Address = Address.apply(ByteVector(bytes))

  @JSExport("fromLong")
  def apply(addr: Long): Address = Address.apply(UInt256(addr))

  @JSExport("fromUInt256")
  def apply(uint: UInt256): Address = Address.apply(uint.bytes)

  @JSExport("fromByteVector")
  def apply(bytes: ByteVector): Address = new Address(bytes.takeRight(numBytes.toLong).padLeft(numBytes.toLong))

  def apply(keyPair: KeyPair): Address = Address(keyPair.public.bytes.kec256)
}
