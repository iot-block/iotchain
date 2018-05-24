package jbok.codec

import scodec.Attempt
import scodec.Attempt.Successful
import scodec.bits._

package object hp {
  type Nibbles = BitVector

  def encode(nibbles: Nibbles, isLeaf: Boolean): ByteVector = {
    val isOdd = nibbles.length / 4 % 2 != 0

    val hp = if (isOdd && isLeaf) {
      bin"0011" ++ nibbles
    } else if (isOdd && !isLeaf) {
      bin"0001" ++ nibbles
    } else if (!isOdd && isLeaf) {
      bin"00100000" ++ nibbles
    } else {
      bin"00000000" ++ nibbles
    }

    hp.toByteVector
  }

  def decode(bytes: ByteVector): Attempt[(Boolean, Nibbles)] = {
    val flag = (bytes(0) & (2 << 4)) != 0
    val even = (bytes(0) & (1 << 4)) == 0

    if (even) {
      Successful(flag, bytes.tail.toBitVector)
    } else {
      Successful(flag, bytes.toBitVector.drop(4))
    }
  }
}
