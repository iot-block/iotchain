package jbok

import jbok.common.math.N
import jbok.core.models.UInt256

package object evm {
  def wordsForBytes(n: N): N = N(math.ceil(1.0 * n.toInt / UInt256.size).toInt)
}