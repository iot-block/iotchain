package jbok.core.consensus
import cats.data.NonEmptyList
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
  * 4. verifyHeader: run a consensus upon a received [[BlockHeader]] and yield 3 possible [[Result]]s
  *   - [[Commit]]  we should commit this block to our blockchain branch tip
  *   - [[Stash]]   we should stash this block since it is not decided yet
  *   - [[Discard]] we should discard this block
  *
  */
abstract class Consensus[F[_]](val history: History[F], val pool: BlockPool[F]) {
  def prepareHeader(parentOpt: Option[Block], ommers: List[BlockHeader] = Nil): F[BlockHeader]

  def postProcess(executed: ExecutedBlock[F]): F[ExecutedBlock[F]]

  def mine(executed: ExecutedBlock[F]): F[MinedBlock]

  def verifyHeader(header: BlockHeader): F[Result]
}

object Consensus {
  sealed trait Result
  case object Commit                                   extends Result
  case object Stash                                    extends Result
  case class Discard(reasons: NonEmptyList[Throwable]) extends Result
}
