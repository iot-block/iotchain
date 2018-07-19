package jbok.persistent

import cats.Traverse
import cats.effect.Sync
import cats.implicits._
import fs2.async.Ref
import scodec.bits.ByteVector

class InMemoryKeyValueDB[F[_]](ref: Ref[F, Map[ByteVector, ByteVector]])(implicit F: Sync[F]) extends KeyValueDB[F] {
  override def get(key: ByteVector): F[ByteVector] =
    getOpt(key).map(_.get)

  override def getOpt(key: ByteVector): F[Option[ByteVector]] =
    ref.get.map(_.get(key))

  override def put(key: ByteVector, newVal: ByteVector): F[Unit] =
    ref.modify(_ + (key -> newVal)).void

  override def del(key: ByteVector): F[Unit] =
    ref.modify(_ - key).void

  override def has(key: ByteVector): F[Boolean] =
    ref.get.map(_.contains(key))

  override def keys: F[List[ByteVector]] =
    ref.get.map(_.keys.toList)

  override def toMap: F[Map[ByteVector, ByteVector]] =
    ref.get

  override def writeBatch[G[_] : Traverse](ops: G[(ByteVector, Option[ByteVector])]): F[Unit] =
    ops
      .map {
        case (key, Some(v)) => put(key, v)
        case (key, None)    => del(key)
      }
      .sequence
      .void

  override def clear(): F[Unit] =
    ref.setSync(Map.empty)
}

object InMemoryKeyValueDB {
  def apply[F[_]]()(implicit F: Sync[F]): F[KeyValueDB[F]] =
    for {
      ref <- fs2.async.refOf[F, Map[ByteVector, ByteVector]](Map.empty)
    } yield new InMemoryKeyValueDB[F](ref)
}
