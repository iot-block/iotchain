package jbok.codec.rlp

import java.net.URI
import java.util.concurrent.TimeUnit

import scodec._
import scodec.bits._

import scala.concurrent.duration.Duration

private[rlp] trait BasicCodecs {
  val ubigint: Codec[BigInt] = new Codec[BigInt] {
    val codec = codecs.bytes.xmap[BigInt](
      bytes => {
        if (bytes.isEmpty) 0 else BigInt(1, bytes.toArray)
      },
      bi => {
        require(bi >= 0, "unsigned codec cannot encode negative values")
        val bytes = bi.toByteArray
        ByteVector(if (bytes.head == 0) bytes.tail else bytes)
      }
    )

    override def encode(value: BigInt): Attempt[BitVector]              = codec.encode(value)
    override def decode(bits: BitVector): Attempt[DecodeResult[BigInt]] = codec.decode(bits)
    override def sizeBound: SizeBound                                   = SizeBound.atLeast(1L)
    override def toString: String                                       = "UBigInt"
  }

  val ulong: Codec[Long] = new Codec[Long] {
    val codec = ubigint.xmap[Long](_.toLong, BigInt.apply)

    override def encode(value: Long): Attempt[BitVector]              = codec.encode(value)
    override def decode(bits: BitVector): Attempt[DecodeResult[Long]] = codec.decode(bits)
    override def sizeBound: SizeBound                                 = SizeBound.bounded(1L, 8L)
    override def toString: String                                     = "ULong"
  }

  val uint: Codec[Int] = new Codec[Int] {
    val codec = ubigint.xmap[Int](_.toInt, BigInt.apply)

    override def encode(value: Int): Attempt[BitVector]              = codec.encode(value)
    override def decode(bits: BitVector): Attempt[DecodeResult[Int]] = codec.decode(bits)
    override def sizeBound: SizeBound                                = SizeBound.bounded(1L, 4L)
    override def toString: String                                    = "UInt"
  }

  val bool: Codec[Boolean] = new Codec[Boolean] {
    val fb    = hex"00"
    val tb    = hex"01"
    val codec = codecs.bytes(1).xmap[Boolean](bytes => if (bytes == fb) false else true, b => if (b) tb else fb)

    override def encode(value: Boolean): Attempt[BitVector]              = codec.encode(value)
    override def decode(bits: BitVector): Attempt[DecodeResult[Boolean]] = codec.decode(bits)
    override def sizeBound: SizeBound                                    = SizeBound.exact(1L)
    override def toString: String                                        = "Boolean"
  }

  val duration: Codec[Duration] =
    ulong.xmap[Duration](l => Duration.apply(l, TimeUnit.NANOSECONDS), d => d.toNanos)

  val uri: Codec[URI] =
    codecs.utf8.xmap(str => new URI(str), _.toString)

  val arrayByte: Codec[Array[Byte]] =
    codecs.bytes.xmap(_.toArray, ByteVector.apply)

  def nop[A](implicit codec: Codec[A]): RlpCodec[A] =
    RlpCodec[A](PrefixType.NoPrefix, codec)

  def rlp[A](implicit codec: Codec[A]): RlpCodec[A] =
    RlpCodec[A](PrefixType.ItemLenPrefix, codec)

  def rlplist[A](implicit codec: Codec[A]): RlpCodec[A] =
    RlpCodec[A](PrefixType.ListLenPrefix, codec)
}
