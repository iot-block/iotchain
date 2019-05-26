package jbok.evm

import cats.effect.Sync
import cats.implicits._
import jbok.core.models.UInt256
import jbok.core.store.ColumnFamilies
import jbok.persistent._
import jbok.codec.rlp.implicits._

final case class Storage[F[_]: Sync](store: StageKVStore[F, UInt256, UInt256]) {
  def store(offset: UInt256, value: UInt256): F[Storage[F]] = Sync[F].pure {
    if (value == UInt256.Zero) {
      this.copy(store = store.del(offset))
    } else {
      this.copy(store = store.put(offset, value))
    }
  }

  def load(offset: UInt256): F[UInt256] = store.get(offset).map(_.getOrElse(UInt256.Zero))

  def commit: F[Storage[F]] = store.commit.map(db2 => this.copy(store = db2))

  def data: F[Map[UInt256, UInt256]] = store.toMap
}

object Storage {
  def empty[F[_]: Sync]: F[Storage[F]] =
    for {
      store <- MemoryKVStore[F]
      stage = StageKVStore(SingleColumnKVStore[F, UInt256, UInt256](ColumnFamilies.Node, store))
    } yield Storage[F](stage)

  def fromMap[F[_]: Sync](kvs: Map[UInt256, UInt256]): F[Storage[F]] =
    for {
      storage <- empty[F]
      stored <- kvs.toList.foldLeftM(storage) {
        case (s, (k, v)) =>
          s.store(k, v)
      }
    } yield stored

  def fromList[F[_]: Sync](words: List[UInt256]): F[Storage[F]] =
    for {
      storage <- empty[F]
      stored <- words.zipWithIndex.map { case (w, i) => UInt256(i) -> w }.foldLeftM(storage) {
        case (s, (k, v)) =>
          s.store(k, v)
      }
    } yield stored
}
