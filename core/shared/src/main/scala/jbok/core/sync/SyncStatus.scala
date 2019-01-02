package jbok.core.sync
import jbok.core.peer.Peer

sealed trait SyncStatus
object SyncStatus {
  final case object Booting                                                        extends SyncStatus
  final case class FastSyncing(target: BigInt)                                     extends SyncStatus
  final case class FullSyncing[F[_]](peer: Peer[F], start: BigInt, target: BigInt) extends SyncStatus
  final case object SyncDone                                                       extends SyncStatus
}
