package jbok.codec

import jbok.codec.rlp.RlpEncoded
import scodec.Attempt
import scodec.bits._

object HexPrefix {
  final case class Nibbles private (value: String) extends AnyVal {
    def head: Char = value.head

    def tail: Nibbles = Nibbles(value.tail)

    def drop(n: Int): Nibbles = Nibbles(value.drop(n))

    def length: Int = value.length

    def splitAt(n: Int): (Nibbles, Nibbles) = {
      val (l, r) = value.splitAt(n)
      Nibbles(l) -> Nibbles(r)
    }

    def isEmpty: Boolean = value.isEmpty

    def ++(that: Nibbles): Nibbles = Nibbles(value ++ that.value)

    def longestCommonPrefix(that: Nibbles): Int =
      value.zip(that.value).takeWhile(t => t._1 == t._2).length
  }

  object Nibbles {
    def coerce(hex: String): Nibbles =
      Nibbles(hex)
  }

  def encodedToNibbles(encoded: RlpEncoded): Nibbles =
    Nibbles(encoded.bytes.toHex)

  def nibblesToEncoded(nibbles: Nibbles): RlpEncoded =
    RlpEncoded.coerce(ByteVector.fromValidHex(nibbles.value).bits)

  def encode(nibbles: Nibbles, isLeaf: Boolean): ByteVector = {
    val isOdd = nibbles.value.length % 2 != 0

    val hp = if (isOdd && isLeaf) {
      "3" ++ nibbles.value
    } else if (isOdd && !isLeaf) {
      "1" ++ nibbles.value
    } else if (!isOdd && isLeaf) {
      "20" ++ nibbles.value
    } else {
      "00" ++ nibbles.value
    }

    ByteVector.fromValidHex(hp)
  }

  def decode(bytes: ByteVector): Attempt[(Boolean, Nibbles)] = {
    val flag = (bytes(0) & (2 << 4)) != 0
    val even = (bytes(0) & (1 << 4)) == 0

    if (even) {
      Attempt.successful((flag, Nibbles(bytes.tail.toHex)))
    } else {
      Attempt.successful((flag, Nibbles(bytes.toBitVector.drop(4).toHex)))
    }
  }
}
