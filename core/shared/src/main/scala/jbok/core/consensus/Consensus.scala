package jbok.core.consensus

import jbok.core.History
import jbok.core.models._
import jbok.core.pool.BlockPool

sealed trait ConsensusResult
object ConsensusResult {
  case object ImportToTop               extends ConsensusResult
  case object Pooled                    extends ConsensusResult
  case class BlockInvalid(e: Throwable) extends ConsensusResult
}

abstract class Consensus[F[_]](val history: History[F], val blockPool: BlockPool[F]) {

  /**
    * 1. common header validate
    * 2. specific header validate
    * 3. validate block and header
    * 4. validate ommers
    * 5. execute
    * 6. post validate
    */
  def semanticValidate(parentHeader: BlockHeader, block: Block): F[Unit]

  def calcDifficulty(blockTime: Long, parentHeader: BlockHeader): F[BigInt]

  def calcGasLimit(parentGas: BigInt): BigInt = {
    val GasLimitBoundDivisor: Int = 1024
    val gasLimitDifference        = parentGas / GasLimitBoundDivisor
    parentGas + gasLimitDifference - 1
  }

  def calcBlockMinerReward(blockNumber: BigInt, ommersCount: Int): F[BigInt]

  def calcOmmerMinerReward(blockNumber: BigInt, ommerNumber: BigInt): F[BigInt]

  def getTimestamp: F[Long]

  def prepareHeader(parent: Block, ommers: List[BlockHeader]): F[BlockHeader]

  def run(parent: Block, current: Block): F[ConsensusResult]

  def mine(block: Block): F[Block]
}
