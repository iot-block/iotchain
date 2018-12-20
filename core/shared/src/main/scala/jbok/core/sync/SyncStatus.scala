package jbok.core.sync

sealed trait SyncStatus
object SyncStatus {
  case object Booting                    extends SyncStatus
  case class FastSyncing(target: BigInt) extends SyncStatus
  case class FullSyncing(target: BigInt) extends SyncStatus
  case object SyncDone                   extends SyncStatus
}
