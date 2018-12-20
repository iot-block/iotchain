package jbok.core.sync
import jbok.core.peer.Peer

sealed trait SyncStatus
object SyncStatus {
  case object Booting                                                        extends SyncStatus
  case class FastSyncing(target: BigInt)                                     extends SyncStatus
  case class FullSyncing[F[_]](peer: Peer[F], start: BigInt, target: BigInt) extends SyncStatus
  case object SyncDone                                                       extends SyncStatus
}
