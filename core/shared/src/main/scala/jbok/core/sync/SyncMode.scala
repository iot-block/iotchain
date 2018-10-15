package jbok.core.sync

sealed trait SyncMode
object SyncMode {
  case object Full  extends SyncMode
  case object Fast  extends SyncMode
  case object Light extends SyncMode
}
