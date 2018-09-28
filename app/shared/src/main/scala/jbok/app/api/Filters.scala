package jbok.app.api

import jbok.core.models.Address
import scodec.bits.ByteVector

case class TxLog(
    logIndex: BigInt,
    transactionIndex: BigInt,
    transactionHash: ByteVector,
    blockHash: ByteVector,
    blockNumber: BigInt,
    address: Address,
    data: ByteVector,
    topics: List[ByteVector]
)

sealed trait FilterChanges
case class LogFilterChanges(logs: List[TxLog])                         extends FilterChanges
case class BlockFilterChanges(blockHashes: List[ByteVector])           extends FilterChanges
case class PendingTransactionFilterChanges(txHashes: List[ByteVector]) extends FilterChanges

sealed trait FilterLogs
case class LogFilterLogs(logs: List[TxLog])                         extends FilterLogs
case class BlockFilterLogs(blockHashes: List[ByteVector])           extends FilterLogs
case class PendingTransactionFilterLogs(txHashes: List[ByteVector]) extends FilterLogs

sealed trait Filter {
  def id: BigInt
}
case class LogFilter(
    id: BigInt,
    fromBlock: Option[BlockParam],
    toBlock: Option[BlockParam],
    address: Option[Address],
    topics: List[List[ByteVector]]
) extends Filter
case class BlockFilter(id: BigInt)              extends Filter
case class PendingTransactionFilter(id: BigInt) extends Filter
