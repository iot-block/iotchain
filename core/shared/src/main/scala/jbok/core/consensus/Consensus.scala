package jbok.core.consensus

import cats.data.NonEmptyList
import jbok.core.consensus.Consensus._
import jbok.core.ledger.History
import jbok.core.ledger.TypedBlock.{ExecutedBlock, MinedBlock}
import jbok.core.models.{Block, BlockHeader}
import jbok.core.pool.BlockPool

/**
  * [[Consensus]] is mainly responsible for
  *   - prepareHeader: generate a [[BlockHeader]] with protocol-specific consensus fields
  *   - postProcess: post process a [[ExecutedBlock]] such as paying reward
  *   - mine: seal a [[ExecutedBlock]] into a [[MinedBlock]]
  *   - verify: check a block fields validity
  *   - run: run a consensus upon a received [[Block]] and yield 4 possible [[Result]]s
  *     - [[Forward]] we should apply blocks and forward
  *     - [[Fork]] we should resolve to a new branch
  *     - [[Stash]]   we should stash this block since it is not decided yet
  *     - [[Discard]] we should discard this block
  *   - resolveBranch: resolve a list of headers
  */
abstract class Consensus[F[_]](val history: History[F], val pool: BlockPool[F]) {
  def prepareHeader(parentOpt: Option[Block], ommers: List[BlockHeader] = Nil): F[BlockHeader]

  def postProcess(executed: ExecutedBlock[F]): F[ExecutedBlock[F]]

  def mine(executed: ExecutedBlock[F]): F[MinedBlock]

  def verify(block: Block): F[Unit]

  def run(block: Block): F[Result]

  def resolveBranch(headers: List[BlockHeader]): F[BranchResult]
}

object Consensus {
  sealed trait Result
  final case class Forward(blocks: List[Block])                         extends Result
  final case class Fork(oldBranch: List[Block], newBranch: List[Block]) extends Result
  final case class Stash(block: Block)                                  extends Result
  final case class Discard(reason: Throwable)                           extends Result

  sealed trait BranchResult
  final case class BetterBranch(newBranch: NonEmptyList[BlockHeader]) extends BranchResult
  final case object NoChainSwitch                                     extends BranchResult
  final case object InvalidBranch                                     extends BranchResult
}
