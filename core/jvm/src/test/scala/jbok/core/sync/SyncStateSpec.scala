package jbok.core.sync

import jbok.JbokSpec
import jbok.core.Fixtures
import scodec.bits.ByteVector

class SyncStateSpec extends JbokSpec {
  def toStateMptNodeHash(seq: String*): List[StateMptNodeHash] =
    seq.toList.map(s => StateMptNodeHash(ByteVector(s.getBytes)))

  def toEvmCodeHash(seq: String*): List[EvmCodeHash] = seq.toList.map(s => EvmCodeHash(ByteVector(s.getBytes)))

//  "sync state" should {
//    "prepend mpt nodes when enqueueing them" in {
//      val syncState = SyncState(
//        targetBlock = Fixtures.Blocks.ValidBlock.header,
//        pendingMptNodes = toStateMptNodeHash("1", "2", "3"),
//        pendingNonMptNodes = toEvmCodeHash("a", "b", "c")
//      )
//
//      val resultingSyncState = syncState
//        .addPendingNodes(toStateMptNodeHash("4", "5", "6"))
//        .addPendingNodes(toEvmCodeHash("d", "e", "f"))
//
//      resultingSyncState.pendingMptNodes shouldEqual toStateMptNodeHash("4", "5", "6", "1", "2", "3")
//      resultingSyncState.pendingNonMptNodes shouldEqual toEvmCodeHash("d", "e", "f", "a", "b", "c")
//    }
//  }
}
