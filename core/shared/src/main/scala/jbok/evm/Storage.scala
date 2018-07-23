package jbok.evm

import cats.effect.Sync
import cats.implicits._
import jbok.core.models.UInt256
import jbok.persistent.{KeyValueDB, KeyValueStore, SnapshotKeyValueStore}
import scodec.bits.ByteVector

case class Storage[F[_]: Sync](db: SnapshotKeyValueStore[F, UInt256, UInt256]) {
  def store(offset: UInt256, value: UInt256): Storage[F] = {
    if (value == UInt256.Zero) {
      this.copy(db = db.del(offset))
    } else {
      this.copy(db = db.put(offset, value))
    }
  }

  def load(offset: UInt256): F[UInt256] = db.getOpt(offset).map(_.getOrElse(UInt256.Zero))

  def commit: F[Storage[F]] = db.commit().map(db2 => this.copy(db = db2))

  def data: F[Map[UInt256, UInt256]] = db.toMap
}

object Storage {
  def empty[F[_]: Sync]: F[Storage[F]] =
    for {
      db <- KeyValueDB.inMemory[F]
      store = new KeyValueStore[F, UInt256, UInt256](ByteVector.empty, db)
      s = SnapshotKeyValueStore(store)
    } yield Storage[F](s)

  def fromMap[F[_]: Sync](kvs: Map[UInt256, UInt256]): F[Storage[F]] =
    for {
      storage <- empty[F]
      stored = kvs.foldLeft(storage) {
        case (s, (k, v)) =>
          s.store(k, v)
      }
    } yield stored

  def fromList[F[_]: Sync](words: List[UInt256]): F[Storage[F]] =
    for {
      storage <- empty[F]
      stored = words.zipWithIndex.map { case (w, i) => UInt256(i) -> w }.foldLeft(storage) {
        case (s, (k, v)) =>
          s.store(k, v)
      }
    } yield stored
}
