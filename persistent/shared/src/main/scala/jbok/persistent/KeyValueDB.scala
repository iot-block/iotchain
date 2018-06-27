package jbok.persistent

import cats.effect.Sync
import scodec.bits.ByteVector

trait KeyValueDB[F[_]] {
  def get(key: ByteVector): F[ByteVector]

  def getOpt(key: ByteVector): F[Option[ByteVector]]

  def put(key: ByteVector, newVal: ByteVector): F[Unit]

  def del(key: ByteVector): F[Unit]

  def has(key: ByteVector): F[Boolean]

  def keys: F[List[ByteVector]]

  def clear(): F[Unit]
}

object KeyValueDB {
  def inMemory[F[_]: Sync]: F[KeyValueDB[F]] = InMemoryKeyValueDB[F]
}