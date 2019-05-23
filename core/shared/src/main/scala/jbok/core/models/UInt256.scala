package jbok.core.models

import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import scodec.bits.ByteVector
import scodec.codecs

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

/** Represents 256 bit unsigned integers with standard arithmetic, byte-wise operation and EVM-specific extensions */
final class UInt256 private (private val n: BigInt) extends Ordered[UInt256] {

  import UInt256._
  require(n >= 0 && n < Modulus, s"Invalid UInt256 value: $n")

  /** Converts a BigInt to a ByteVector.
    *  Output ByteVector is padded with 0's from the left side up to UInt256.Size bytes.
    */
  lazy val bytes: ByteVector =
    ByteVector(n.toByteArray).takeRight(Size).padLeft(Size)

  lazy val unpaddedBytes: ByteVector =
    ByteVector(n.toByteArray).dropWhile(_ == 0.toByte).takeRight(Size)

  /** Used for gas calculation for EXP opcode. See YP Appendix H.1 (220)
    * For n > 0: (n.bitLength - 1) / 8 + 1 == 1 + floor(log_256(n))
    *
    * @return Size in bytes excluding the leading 0 bytes
    */
  def byteSize: Int = if (isZero) 0 else (n.bitLength - 1) / 8 + 1

  def getByte(that: UInt256): UInt256 =
    if (that.n > 31) Zero else UInt256(bytes(that.n.toInt).toInt & 0xff)

  // standard arithmetic (note the use of new instead of apply where result is guaranteed to be within bounds)
  def &(that: UInt256): UInt256 = UInt256(this.n & that.n)

  def |(that: UInt256): UInt256 = UInt256(this.n | that.n)

  def ^(that: UInt256): UInt256 = UInt256(this.n ^ that.n)

  def unary_- : UInt256 = UInt256(-n)

  def unary_~ : UInt256 = UInt256(~n)

  def +(that: UInt256): UInt256 = UInt256(this.n + that.n)

  def -(that: UInt256): UInt256 = UInt256(this.n - that.n)

  def *(that: UInt256): UInt256 = UInt256(this.n * that.n)

  def /(that: UInt256): UInt256 = UInt256(this.n / that.n)

  def **(that: UInt256): UInt256 = UInt256(this.n.modPow(that.n, Modulus))

  def compare(that: UInt256): Int = this.n compare that.n

  def min(that: UInt256): UInt256 = if (compare(that) < 0) this else that

  def max(that: UInt256): UInt256 = if (compare(that) > 0) this else that

  def isZero: Boolean = n == 0

  // EVM-specific arithmetic
  private lazy val signedN: BigInt = if (n > MaxSignedValue) n - Modulus else n

  private def zeroCheck(x: UInt256)(result: => BigInt): UInt256 =
    if (x.isZero) Zero else UInt256(result)

  def div(that: UInt256): UInt256 = zeroCheck(that) { UInt256(this.n / that.n) }

  def sdiv(that: UInt256): UInt256 = zeroCheck(that) { UInt256(this.signedN / that.signedN) }

  def mod(that: UInt256): UInt256 = zeroCheck(that) { UInt256(this.n.mod(that.n)) }

  def smod(that: UInt256): UInt256 = zeroCheck(that) { UInt256(this.signedN % that.signedN.abs) }

  def addmod(that: UInt256, modulus: UInt256): UInt256 = zeroCheck(modulus) {
    UInt256((this.n + that.n) % modulus.n)
  }

  def mulmod(that: UInt256, modulus: UInt256): UInt256 = zeroCheck(modulus) {
    UInt256((this.n * that.n).mod(modulus.n))
  }

  def slt(that: UInt256): Boolean = this.signedN < that.signedN

  def sgt(that: UInt256): Boolean = this.signedN > that.signedN

  def <<(that: UInt256): UInt256 = if (that.n >= 256) UInt256.Zero else UInt256(this.n << that.n.toInt)

  def >>(that: UInt256): UInt256 =
    if (that.n >= 256)
      if (this.signedN < 0)
        UInt256.MaxValue
      else
        UInt256.Zero
    else
      UInt256(this.signedN >> that.n.toInt)

  def >>>(that: UInt256): UInt256 = if (that.n >= 256) UInt256.Zero else UInt256(this.n >> that.n.toInt)

  def signExtend(that: UInt256): UInt256 =
    if (that.n < 0 || that.n > 31) {
      this
    } else {
      val idx      = that.n.toByte
      val negative = n.testBit(idx * 8 + 7)
      val mask     = (BigInt(1) << ((idx + 1) * 8)) - 1
      val newN     = if (negative) n | (MaxValue ^ mask) else n & mask
      UInt256(newN)
    }

  //standard methods
  override def equals(that: Any): Boolean =
    that match {
      case that: UInt256 => this.n.equals(that.n)
      case other         => other == n
    }

  override def hashCode: Int = n.hashCode()

  @JSExport
  override def toString: String = toSignedDecString

  def toDecString: String =
    n.toString

  def toSignedDecString: String =
    signedN.toString

  @JSExport
  def toHexString: String = {
    val hex = f"$n%x"
    //add zero if odd number of digits
    val extraZero = if (hex.length % 2 == 0) "" else "0"
    s"0x$extraZero$hex"
  }

  // conversions
  @JSExport
  def toBigInt: BigInt = n

  /**
    * @return an Int with MSB=0, thus a value in range [0, Int.MaxValue]
    */
  @JSExport
  def toInt: Int = n.intValue & Int.MaxValue

  /**
    * @return a Long with MSB=0, thus a value in range [0, Long.MaxValue]
    */
  @JSExport
  def toLong: Long = n.longValue & Long.MaxValue
}

@JSExportTopLevel("UInt256")
object UInt256 {
  implicit val decodeUInt256: io.circe.Decoder[UInt256] = io.circe.Decoder.decodeBigInt.map(UInt256.apply)

  implicit val encodeUInt256: io.circe.Encoder[UInt256] = io.circe.Encoder.encodeBigInt.contramap(_.toBigInt)

  implicit val codec: RlpCodec[UInt256] = rlp(codecs.bytes.xmap[UInt256](UInt256.apply, _.unpaddedBytes))

  /** Size of UInt256 byte representation */
  val Size: Int = 32

  private val Modulus: BigInt = BigInt(2).pow(256)

  val MaxValue: UInt256 = UInt256(Modulus - 1)

  val Zero: UInt256 = UInt256(0)

  val One: UInt256 = UInt256(1)

  val Two: UInt256 = UInt256(2)

  @JSExport("fromByteVector")
  def apply(bytes: ByteVector): UInt256 = {
    require(bytes.length <= Size, s"Input byte array cannot be longer than $Size: ${bytes.length}")
    UInt256(BigInt(1, bytes.toArray))
  }

  def apply(array: Array[Byte]): UInt256 =
    UInt256(ByteVector(array))

  @JSExport("fromBigInt")
  def apply(n: BigInt): UInt256 =
    new UInt256(boundBigInt(n))

  def apply(b: Boolean): UInt256 =
    if (b) One else Zero

  @JSExport("fromLong")
  def apply(n: Long): UInt256 =
    apply(BigInt(n))

  implicit class BigIntAsUInt256(val bigInt: BigInt) extends AnyVal {
    def toUInt256: UInt256 = UInt256(bigInt)
  }

  implicit def uint256ToBigInt(uint: UInt256): BigInt = uint.toBigInt

  implicit def byte2UInt256(b: Byte): UInt256 = UInt256(b)

  implicit def int2UInt256(i: Int): UInt256 = UInt256(i)

  implicit def long2UInt256(l: Long): UInt256 = UInt256(l)

  implicit def bool2UInt256(b: Boolean): UInt256 = UInt256(b)

  private def boundBigInt(n: BigInt): BigInt = (n % Modulus + Modulus) % Modulus

  private val MaxSignedValue: BigInt = BigInt(2).pow(Size * 8 - 1) - 1
}
