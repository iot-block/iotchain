package jbok.persistent

import cats.effect.Sync
import cats.implicits._
import fs2._
import jbok.codec.rlp.RlpCodec
import scodec.bits.ByteVector
import jbok.codec.rlp.implicits._

trait SingleColumnKVStore[F[_], K, V] {
  def cf: ColumnFamily

  def put(key: K, value: V): F[Unit]

  def get(key: K): F[Option[V]]

  def del(key: K): F[Unit]

  def writeBatch(puts: List[(K, V)], dels: List[K]): F[Unit]

  def writeBatch(ops: List[(K, Option[V])]): F[Unit]

  def toStream: Stream[F, (K, V)]

  def toList: F[List[(K, V)]]

  def toMap: F[Map[K, V]]

  def size: F[Int]

  def decodeTuple(kv: (ByteVector, ByteVector)): F[(K, V)]

  def encodeTuple(kv: (K, V)): (ByteVector, ByteVector)
}

object SingleColumnKVStore {
  def apply[F[_], K: RlpCodec, V: RlpCodec](columnFamily: ColumnFamily, store: KVStore[F])(implicit F: Sync[F]): SingleColumnKVStore[F, K, V] = new SingleColumnKVStore[F, K, V] {
    override def cf: ColumnFamily = columnFamily

    override def put(key: K, value: V): F[Unit] =
      store.put(cf, key.asValidBytes, value.asValidBytes)

    override def get(key: K): F[Option[V]] =
      store.getAs[V](cf, key.asValidBytes)

    override def del(key: K): F[Unit] =
      store.del(cf, key.asValidBytes)

    override def writeBatch(puts: List[(K, V)], dels: List[K]): F[Unit] =
      store.writeBatch(cf, puts.map(encodeTuple), dels.map(_.asValidBytes))

    override def writeBatch(ops: List[(K, Option[V])]): F[Unit] =
      store.writeBatch(cf, ops.map { case (k, v) => k.asValidBytes -> v.map(_.asValidBytes) })

    override def toStream: Stream[F, (K, V)] =
      store.toStream(cf).evalMap(decodeTuple)

    override def toList: F[List[(K, V)]] =
      store.toList(cf).flatMap(_.traverse(decodeTuple))

    override def toMap: F[Map[K, V]] =
      toList.map(_.toMap)

    override def size: F[Int] =
      store.size(cf)

    override def encodeTuple(kv: (K, V)): (ByteVector, ByteVector) =
      (kv._1.asValidBytes, kv._2.asValidBytes)

    override def decodeTuple(kv: (ByteVector, ByteVector)): F[(K, V)] =
      for {
        key   <- F.fromEither(kv._1.asEither[K])
        value <- F.fromEither(kv._2.asEither[V])
      } yield key -> value
  }
}
