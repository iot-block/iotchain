package jbok.codec.rlp

import java.nio.charset.StandardCharsets

import jbok.codec.rlp.RlpCodec.item
import scodec.bits.ByteVector
import scodec.codecs._

object codecs {
  implicit val rempty: RlpCodec[Unit] = RlpCodec.pure(constant(RlpCodec.itemOffset))

  implicit val rbyte: RlpCodec[Byte] = item(byte)

  implicit val rbytes: RlpCodec[ByteVector] = item(bytes)

  implicit val rbyteslist: RlpCodec[List[ByteVector]] = RlpCodec.deriveList(rbytes)

  implicit val rbytearray: RlpCodec[Array[Byte]] = item(bytes.xmap[Array[Byte]](_.toArray, arr => ByteVector(arr)))

  implicit val rstring: RlpCodec[String] = item(string(StandardCharsets.UTF_8))

  implicit val rvint: RlpCodec[Int] = item(
    bytes.xmap[Int](
      bytes =>
        bytes.length match {
          case 0 => 0
          case 1 => bytes(0) & 0xFF
          case 2 => ((bytes(0) & 0xFF) << 8) + (bytes(1) & 0xFF)
          case 3 => ((bytes(0) & 0xFF) << 16) + ((bytes(1) & 0xFF) << 8) + (bytes(2) & 0xFF)
          case Integer.BYTES =>
            ((bytes(0) & 0xFF) << 24) + ((bytes(1) & 0xFF) << 16) + ((bytes(2) & 0xFF) << 8) + (bytes(3) & 0xFF)
          case _ => throw new RuntimeException("bytes it not an int")
      },
      i => {
        if (i == (i & 0xFF)) byteToBytes(i.toByte)
        else if (i == (i & 0xFFFF)) shortToBytes(i.toShort)
        else if (i == (i & 0xFFFFFF)) ByteVector((i >>> 16).toByte, (i >>> 8).toByte, i.toByte)
        else ByteVector((i >>> 24).toByte, (i >>> 16).toByte, (i >>> 8).toByte, i.toByte)
      }
    ))

  implicit val rvshort: RlpCodec[Short] =
    item(
      bytes.xmap[Short](
        bytes =>
          bytes.length match {
            case 0 => 0
            case 1 => (bytes(0) & 0xFF).toShort
            case 2 => (((bytes(0) & 0xFF) << 8) + (bytes(1) & 0xFF)).toShort
            case _ => throw new RuntimeException("bytes is not a short")
        },
        short => shortToBytes(short)
      ))

  implicit val rulong: RlpCodec[Long] = item(ulong(63))

  implicit val rubigint: RlpCodec[BigInt] =
    item(bytes.xmap[BigInt](bytes => if (bytes.isEmpty) 0 else BigInt(1, bytes.toArray), bi => {
      val bytes = bi.toByteArray
      ByteVector(if (bytes.head == 0) bytes.tail else bytes)
    }))

  implicit val rbool: RlpCodec[Boolean] = item(bool(8))

  implicit def roptional[A: RlpCodec]: RlpCodec[Option[A]] =
    item(optional(bool(8), RlpCodec[A].valueCodec))

  implicit def reither[L: RlpCodec, R: RlpCodec] =
    item(either[L, R](bool(8), RlpCodec[L].valueCodec, RlpCodec[R].valueCodec))

  private def byteToBytes(byte: Byte): ByteVector =
    if ((byte & 0xFF) == 0) ByteVector.empty
    else ByteVector(byte)

  private def shortToBytes(short: Short): ByteVector =
    if ((short & 0xFF) == short) byteToBytes(short.toByte)
    else ByteVector((short >> 8 & 0xFF).toByte, (short >> 0 & 0xFF).toByte)

}
