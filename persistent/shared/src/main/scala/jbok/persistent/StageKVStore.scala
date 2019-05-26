package jbok.persistent

import cats.effect.Sync
import cats.implicits._

final case class StageKVStore[F[_], K, V](inner: SingleColumnKVStore[F, K, V], stage: Map[K, Option[V]])(implicit F: Sync[F]) {
  def mustGet(key: K): F[V] =
    get(key).flatMap(opt => F.fromOption(opt, new Exception(s"fatal db failure, key=${key} not found")))

  def put(key: K, value: V): StageKVStore[F, K, V] = copy(stage = stage + (key -> Some(value)))

  def get(key: K): F[Option[V]] =
    stage.get(key) match {
      case Some(valueOpt) => valueOpt.pure[F]
      case None           => inner.get(key)
    }

  def del(key: K): StageKVStore[F, K, V] =
    copy(stage = stage + (key -> None))

  def toMap: F[Map[K, V]] =
    inner.toMap.map(kvs => kvs ++ stage.collect { case (k, Some(v)) => k -> v })

  def commit: F[StageKVStore[F, K, V]] =
    for {
      _ <- inner.writeBatch(stage.toList)
    } yield copy[F, K, V](stage = Map.empty)

  def +(kv: (K, V)): StageKVStore[F, K, V] =
    copy(stage = stage + (kv._1 -> Some(kv._2)))

  def ++(kvs: Map[K, V]): StageKVStore[F, K, V] =
    copy(stage = stage ++ kvs.mapValues(Some.apply))
}

object StageKVStore {
  def apply[F[_]: Sync, K, V](inner: SingleColumnKVStore[F, K, V]): StageKVStore[F, K, V] =
    StageKVStore[F, K, V](inner, Map.empty[K, Option[V]])
}
