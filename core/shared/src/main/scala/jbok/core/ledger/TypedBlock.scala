package jbok.core.ledger
import jbok.core.models.{Block, Receipt}
import jbok.core.peer.{Peer, PeerSet}
import jbok.evm.WorldState

sealed trait TypedBlock
object TypedBlock {
  case class ExecutedBlock[F[_]](
      block: Block,
      world: WorldState[F],
      gasUsed: BigInt,
      receipts: List[Receipt],
      td: BigInt
  ) extends TypedBlock
  case class MinedBlock(block: Block, receipts: List[Receipt])                     extends TypedBlock
  case class ReceivedBlock[F[_]](block: Block, peer: Peer[F], peerSet: PeerSet[F]) extends TypedBlock
  case class RequestedBlocks[F[_]](blocks: List[Block], peer: Peer[F])             extends TypedBlock
  case class PendingBlock(block: Block)                                            extends TypedBlock
}
