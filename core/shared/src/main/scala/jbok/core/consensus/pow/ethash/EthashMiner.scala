package jbok.core.consensus.pow.ethash

import jbok.core.models.Block

trait EthashMiner[F[_]] {
  def mine(block: Block): F[Block]
}
