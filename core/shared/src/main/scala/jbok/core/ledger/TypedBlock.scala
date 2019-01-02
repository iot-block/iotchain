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
  *  - [[SyncBlocks]] block from full sync or other sync source such as file
  *  - [[PendingBlock]] block with prepared headers and transactions
  */
sealed trait TypedBlock
object TypedBlock {
  final case class ExecutedBlock[F[_]](
      block: Block,
      world: WorldState[F],
      gasUsed: BigInt,
      receipts: List[Receipt],
      td: BigInt
  ) extends TypedBlock
  final case class MinedBlock(block: Block, receipts: List[Receipt])               extends TypedBlock
  final case class ReceivedBlock[F[_]](block: Block, peer: Peer[F])                extends TypedBlock
  final case class SyncBlocks[F[_]](blocks: List[Block], peerOpt: Option[Peer[F]]) extends TypedBlock
  final case class PendingBlock(block: Block)                                      extends TypedBlock
}
