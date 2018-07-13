package jbok.persistent

import cats.Traverse
import cats.effect.Sync
import cats.implicits._
import fs2.async.Ref
import scodec.bits.ByteVector

case class BatchKeyValueDB[F[_]: Sync](kv: KeyValueDB[F], stage: Ref[F, Map[ByteVector, Option[ByteVector]]])
    extends KeyValueDB[F] {
  override def get(key: ByteVector): F[ByteVector] = kv.get(key)

  override def getOpt(key: ByteVector): F[Option[ByteVector]] = kv.getOpt(key)

  override def put(key: ByteVector, newVal: ByteVector): F[Unit] = kv.put(key, newVal)

  override def del(key: ByteVector): F[Unit] = kv.del(key)

  override def has(key: ByteVector): F[Boolean] = kv.has(key)

  override def keys: F[List[ByteVector]] = kv.keys

  override def writeBatch[G[_]: Traverse](ops: G[(ByteVector, Option[ByteVector])]): F[Unit] = kv.writeBatch(ops)

  override def clear(): F[Unit] = kv.clear()

  def addPut(key: ByteVector, newVal: ByteVector): F[Unit] =
    stage.modify(_ + (key -> Some(newVal))).void

  def addDel(key: ByteVector): F[Unit] =
    stage.modify(m => (m - key) + (key -> None)).void

  def commit(): F[Unit] =
    for {
      m <- stage.get
      _ <- writeBatch(m.toList)
      _ <- stage.setSync(Map.empty)
    } yield ()
}

object BatchKeyValueDB {
  def fromKV[F[_]: Sync](kv: KeyValueDB[F]): F[BatchKeyValueDB[F]] =
    for {
      stage <- fs2.async.refOf[F, Map[ByteVector, Option[ByteVector]]](Map.empty)
    } yield new BatchKeyValueDB[F](kv, stage)
}
