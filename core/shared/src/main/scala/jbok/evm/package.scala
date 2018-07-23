package jbok

import jbok.core.models.UInt256

package object evm {
  def wordsForBytes(n: BigInt): BigInt = math.ceil(1.0 * n.toInt / UInt256.Size).toInt
}