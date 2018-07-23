package jbok.persistent

import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._

case class SnapshotKeyValueStore[F[_]: Sync, K, V](inner: KeyValueStore[F, K, V], stage: Map[K, Option[V]]) {
  def get(key: K): F[V] = getOpt(key).map(_.get)

  def getOpt(key: K): F[Option[V]] = OptionT.fromOption[F](stage.get(key)).getOrElseF(inner.getOpt(key))

  def put(key: K, newVal: V): SnapshotKeyValueStore[F, K, V] =
    this.copy(stage = stage + (key -> Some(newVal)))

  def del(key: K): SnapshotKeyValueStore[F, K, V] =
    this.copy(stage = stage + (key -> None))

  def has(key: K): F[Boolean] = if (stage.contains(key)) true.pure[F] else inner.has(key)

  def keys: F[List[K]] = inner.keys.map(ks => ks ++ stage.filter { case (_, v) => v.isDefined }.keys.toList)

  def toMap: F[Map[K, V]] = inner.toMap.map(kvs => kvs ++ stage.collect { case (k, Some(v)) => k -> v })

  def commit(): F[SnapshotKeyValueStore[F, K, V]] =
    for {
      _ <- inner.writeBatch(stage.toList)
    } yield new SnapshotKeyValueStore[F, K, V](inner, Map.empty)

  def +(kv: (K, V)): SnapshotKeyValueStore[F, K, V] = this.copy(stage = stage + (kv._1 -> Some(kv._2)))

  def ++(kvs: Map[K, V]): SnapshotKeyValueStore[F, K, V] = this.copy(stage = stage ++ kvs.mapValues(Some.apply))
}

object SnapshotKeyValueStore {
  def apply[F[_]: Sync, K, V](inner: KeyValueStore[F, K, V]): SnapshotKeyValueStore[F, K, V] =
    new SnapshotKeyValueStore[F, K, V](inner, Map.empty)
}
