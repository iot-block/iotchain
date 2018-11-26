package jbok.core.consensus
import jbok.core.consensus.Consensus._
import jbok.core.ledger.History
import jbok.core.ledger.TypedBlock.{ExecutedBlock, MinedBlock}
import jbok.core.models.{Block, BlockHeader}
import jbok.core.pool.BlockPool

/**
  * [[Consensus]] is mainly responsible for 4 things
  * 1. prepareHeader: generate a [[BlockHeader]] with protocol-specific consensus fields
  * 2. postProcess: post process a [[ExecutedBlock]] such as paying reward
  * 3. mine: seal a [[ExecutedBlock]] into a [[MinedBlock]]
  * 4. run: run a consensus upon a received [[BlockHeader]] and yield 3 possible [[Result]]s
  *   - [[Forward]] we should apply blocks and forward
  *   - [[Resolve]] we should resolve to a new branch
  *   - [[Stash]]   we should stash this block since it is not decided yet
  *   - [[Discard]] we should discard this block
  *
  */
abstract class Consensus[F[_]](val history: History[F], val pool: BlockPool[F]) {
  def prepareHeader(parentOpt: Option[Block], ommers: List[BlockHeader] = Nil): F[BlockHeader]

  def postProcess(executed: ExecutedBlock[F]): F[ExecutedBlock[F]]

  def mine(executed: ExecutedBlock[F]): F[MinedBlock]

  def run(block: Block): F[Result]

  def resolveBranch(headers: List[BlockHeader]): F[BranchResult]
}

object Consensus {
  sealed trait Result
  case class Forward(blocks: List[Block])                            extends Result
  case class Resolve(oldBranch: List[Block], newBranch: List[Block]) extends Result
  case class Stash(block: Block)                                     extends Result
  case class Discard(reason: Throwable)                              extends Result

  sealed trait BranchResult
  case class NewBetterBranch(oldBranch: List[Block]) extends BranchResult
  case object NoChainSwitch                          extends BranchResult
  case object UnknownBranch                          extends BranchResult
  case object InvalidBranch                          extends BranchResult
}
