package jbok.persistent

import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import scodec.Codec
import scodec.bits.ByteVector

case class SnapshotKeyValueDB[F[_]: Sync, K: Codec, V: Codec](
    namespace: ByteVector,
    inner: KeyValueDB[F],
    stage: Map[K, Option[V]]
) {
  def get(key: K): F[V] = getOpt(key).map(_.get)

  def getOpt(key: K): F[Option[V]] =
    OptionT.fromOption[F](stage.get(key)).getOrElseF(inner.getOpt[K, V](key, namespace))

  def put(key: K, newVal: V): SnapshotKeyValueDB[F, K, V] =
    this.copy(stage = stage + (key -> Some(newVal)))

  def del(key: K): SnapshotKeyValueDB[F, K, V] =
    this.copy(stage = stage + (key -> None))

  def has(key: K): F[Boolean] = if (stage.contains(key)) true.pure[F] else inner.has[K](key, namespace)

  def toMap: F[Map[K, V]] =
    inner.toMap[K, V](namespace).map(kvs => kvs ++ stage.collect { case (k, Some(v)) => k -> v })

  def commit: F[SnapshotKeyValueDB[F, K, V]] =
    for {
      _ <- inner.writeBatch[K, V](stage.toList, namespace)
    } yield new SnapshotKeyValueDB[F, K, V](namespace, inner, Map.empty)

  def +(kv: (K, V)): SnapshotKeyValueDB[F, K, V] = this.copy(stage = stage + (kv._1 -> Some(kv._2)))

  def ++(kvs: Map[K, V]): SnapshotKeyValueDB[F, K, V] = this.copy(stage = stage ++ kvs.mapValues(Some.apply))
}

object SnapshotKeyValueDB {
  def apply[F[_]: Sync, K: Codec, V: Codec](namespace: ByteVector, inner: KeyValueDB[F]): SnapshotKeyValueDB[F, K, V] =
    new SnapshotKeyValueDB[F, K, V](namespace, inner, Map.empty)
}
