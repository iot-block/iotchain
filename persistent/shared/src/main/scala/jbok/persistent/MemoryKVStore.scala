package jbok.persistent

import cats.effect.Sync
import cats.implicits._
import cats.effect.concurrent.Ref
import scodec.bits.ByteVector
import fs2._
import jbok.codec.rlp.implicits._

final class MemoryKVStore[F[_]](m: Ref[F, Map[ColumnFamily, Map[ByteVector, ByteVector]]])(implicit F: Sync[F]) extends KVStore[F] {
  override def put(cf: ColumnFamily, key: ByteVector, value: ByteVector): F[Unit] =
    m.update(m => m.updated(cf, m.getOrElse(cf, Map.empty) + (key -> value)))

  override def get(cf: ColumnFamily, key: ByteVector): F[Option[ByteVector]] =
    m.get.map(_.get(cf).flatMap(_.get(key)))

  override def getAs[A: RlpCodec](cf: ColumnFamily, key: ByteVector): F[Option[A]] =
    get(cf, key).flatMap {
      case Some(value) => F.fromEither(value.asEither[A]).map(_.some)
      case None        => F.pure(None)
    }

  override def del(cf: ColumnFamily, key: ByteVector): F[Unit] =
    m.update(m => m.updated(cf, m.getOrElse(cf, Map.empty) - key))

  override def writeBatch(cf: ColumnFamily, puts: List[(ByteVector, ByteVector)], dels: List[ByteVector]): F[Unit] =
    for {
      _ <- puts.traverse { case (key, value) => put(cf, key, value) }
      _ <- dels.traverse { key =>
        del(cf, key)
      }
    } yield ()

  override def writeBatch(cf: ColumnFamily, ops: List[(ByteVector, Option[ByteVector])]): F[Unit] =
    ops.traverse_ {
      case (key, Some(value)) => put(cf, key, value)
      case (key, None)        => del(cf, key)
    }

  override def writeBatch(puts: List[(ColumnFamily, ByteVector, ByteVector)], dels: List[(ColumnFamily, ByteVector)]): F[Unit] =
    for {
      _ <- puts.traverse { case (cf, key, value) => put(cf, key, value) }
      _ <- dels.traverse { case (cf, key)        => del(cf, key) }
    } yield ()

  override def toStream(cf: ColumnFamily): Stream[F, (ByteVector, ByteVector)] =
    Stream.eval(toList(cf)).flatMap(Stream.emits)

  override def toList(cf: ColumnFamily): F[List[(ByteVector, ByteVector)]] =
    toMap(cf).map(_.toList)

  override def toMap(cf: ColumnFamily): F[Map[ByteVector, ByteVector]] =
    m.get.map(_.getOrElse(cf, Map.empty))

  override def size(cf: ColumnFamily): F[Int] =
    m.get.map(_.get(cf).map(_.size).getOrElse(0))
}

object MemoryKVStore {
  def apply[F[_]](implicit F: Sync[F]): F[KVStore[F]] =
    Ref.of[F, Map[ColumnFamily, Map[ByteVector, ByteVector]]](Map.empty).map(ref => new MemoryKVStore[F](ref))
}
