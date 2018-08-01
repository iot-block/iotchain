package jbok.codec

import scodec.Attempt
import scodec.Attempt.Successful
import scodec.bits._

package object HexPrefix {
  type Nibbles = String

  def bytesToNibbles(bytes: ByteVector): Nibbles =
    bytes.toHex

  def encode(nibbles: Nibbles, isLeaf: Boolean): ByteVector = {
    val isOdd = nibbles.length % 2 != 0

    val hp = if (isOdd && isLeaf) {
      "3" ++ nibbles
    } else if (isOdd && !isLeaf) {
      "1" ++ nibbles
    } else if (!isOdd && isLeaf) {
      "20" ++ nibbles
    } else {
      "00" ++ nibbles
    }

    ByteVector.fromValidHex(hp)
  }

  def decode(bytes: ByteVector): Attempt[(Boolean, Nibbles)] = {
    val flag = (bytes(0) & (2 << 4)) != 0
    val even = (bytes(0) & (1 << 4)) == 0

    if (even) {
      Successful((flag, bytes.tail.toHex))
    } else {
      Successful((flag, bytes.toBitVector.drop(4).toHex))
    }
  }
}
