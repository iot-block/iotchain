package jbok.core.store
import jbok.persistent.ColumnFamily

object ColumnFamilies {
  // chain data
  val BlockHeader = ColumnFamily("BlockHeader")
  val BlockBody   = ColumnFamily("BlockBody")
  val Receipts    = ColumnFamily("Receipts")
  val Snapshot    = ColumnFamily("Snapshot")

  // account, storage and code
  val Node = ColumnFamily("Node")
  val Code = ColumnFamily("Code")

  // state
  val TotalDifficulty = ColumnFamily("TotalDifficulty")
  val AppState        = ColumnFamily("AppState")

  // discovered peers
  val Peer = ColumnFamily("Peer")

  // mapping
  val NumberHash = ColumnFamily("NumberHash")
  val TxLocation = ColumnFamily("TxLocation")
}
