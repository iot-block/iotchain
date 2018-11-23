package jbok.core.ledger
import jbok.core.ledger.TypedBlock._
import jbok.core.models.{Block, Receipt}
import jbok.core.peer.Peer
import jbok.evm.WorldState

/**
  * [[TypedBlock]] is an ADT of 5 different kinds of blocks
  *  - [[ExecutedBlock]] block with executed world state, gasUsed, receipts, and totalDifficulty
  *  - [[MinedBlock]] block sealed by the local miner
  *  - [[ReceivedBlock]] block received from peers
  *  - [[RequestedBlocks]] block response from full sync
  *  - [[PendingBlock]] block with prepared headers and transactions
  */
sealed trait TypedBlock
object TypedBlock {
  case class ExecutedBlock[F[_]](
      block: Block,
      world: WorldState[F],
      gasUsed: BigInt,
      receipts: List[Receipt],
      td: BigInt
  ) extends TypedBlock
  case class MinedBlock(block: Block, receipts: List[Receipt])         extends TypedBlock
  case class ReceivedBlock[F[_]](block: Block, peer: Peer[F])          extends TypedBlock
  case class RequestedBlocks[F[_]](blocks: List[Block], peer: Peer[F]) extends TypedBlock
  case class PendingBlock(block: Block)                                extends TypedBlock
}
