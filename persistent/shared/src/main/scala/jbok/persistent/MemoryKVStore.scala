package jbok.persistent

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2._

final class MemoryKVStore[F[_]](m: Ref[F, Map[ColumnFamily, Map[Seq[Byte], Array[Byte]]]])(implicit F: Sync[F]) extends KVStore[F] {
  override def put(cf: ColumnFamily, key: Array[Byte], value: Array[Byte]): F[Unit] =
    m.update(m => m.updated(cf, m.getOrElse(cf, Map.empty) + (key.toSeq -> value)))

  override def del(cf: ColumnFamily, key: Array[Byte]): F[Unit] =
    m.update(m => m.updated(cf, m.getOrElse(cf, Map.empty) - key))

  override def writeBatch(cf: ColumnFamily, puts: List[(Array[Byte], Array[Byte])], dels: List[Array[Byte]]): F[Unit] =
    for {
      _ <- puts.traverse { case (key, value) => put(cf, key, value) }
      _ <- dels.traverse { key =>
        del(cf, key)
      }
    } yield ()

  override def writeBatch(cf: ColumnFamily, ops: List[(Array[Byte], Option[Array[Byte]])]): F[Unit] =
    ops.traverse_ {
      case (key, Some(value)) => put(cf, key, value)
      case (key, None)        => del(cf, key)
    }

  override def writeBatch(puts: List[Put], dels: List[Del]): F[Unit] =
    for {
      _ <- puts.traverse { case (cf, key, value) => put(cf, key, value) }
      _ <- dels.traverse { case (cf, key)        => del(cf, key) }
    } yield ()

  override def get(cf: ColumnFamily, key: Array[Byte]): F[Option[Array[Byte]]] =
    m.get.map(_.get(cf).flatMap(_.get(key)))

  override def toStream(cf: ColumnFamily): Stream[F, (Array[Byte], Array[Byte])] =
    Stream.eval(toList(cf)).flatMap(Stream.emits)

  override def toList(cf: ColumnFamily): F[List[(Array[Byte], Array[Byte])]] =
    toMap(cf).map(_.toList)

  override def toMap(cf: ColumnFamily): F[Map[Array[Byte], Array[Byte]]] =
    m.get.map(_.getOrElse(cf, Map.empty).map { case (k, v) => k.toArray -> v })

  override def size(cf: ColumnFamily): F[Int] =
    m.get.map(_.get(cf).map(_.size).getOrElse(0))
}

object MemoryKVStore {
  def apply[F[_]](implicit F: Sync[F]): F[KVStore[F]] =
    Ref.of[F, Map[ColumnFamily, Map[Seq[Byte], Array[Byte]]]](Map.empty).map(ref => new MemoryKVStore[F](ref))
}
