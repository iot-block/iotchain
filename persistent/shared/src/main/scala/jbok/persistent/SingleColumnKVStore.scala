package jbok.persistent

import cats.effect.Sync
import cats.implicits._
import fs2._
import jbok.codec.rlp.{RlpCodec, RlpEncoded}
import jbok.codec.rlp.implicits._
import scodec.bits.BitVector

trait SingleColumnKVStore[F[_], K, V] {
  def cf: ColumnFamily

  def put(key: K, value: V): F[Unit]

  def del(key: K): F[Unit]

  def writeBatch(puts: List[(K, V)], dels: List[K]): F[Unit]

  def writeBatch(ops: List[(K, Option[V])]): F[Unit]

  def get(key: K): F[Option[V]]

  def toStream: Stream[F, (K, V)]

  def toList: F[List[(K, V)]]

  def toMap: F[Map[K, V]]

  def size: F[Int]
}

object SingleColumnKVStore {
  def apply[F[_], K: RlpCodec, V: RlpCodec](columnFamily: ColumnFamily, store: KVStore[F])(implicit F: Sync[F]): SingleColumnKVStore[F, K, V] = new SingleColumnKVStore[F, K, V] {
    override def cf: ColumnFamily = columnFamily

    override def put(key: K, value: V): F[Unit] =
      store.put(cf, key.encoded.byteArray, value.encoded.byteArray)

    override def del(key: K): F[Unit] =
      store.del(cf, key.encoded.byteArray)

    override def writeBatch(puts: List[(K, V)], dels: List[K]): F[Unit] =
      store.writeBatch(cf, puts.map(encodeTuple), dels.map(_.encoded.byteArray))

    override def writeBatch(ops: List[(K, Option[V])]): F[Unit] =
      store.writeBatch(cf, ops.map { case (k, v) => k.encoded.byteArray -> v.map(_.encoded.byteArray) })

    override def get(key: K): F[Option[V]] =
      store.get(cf, key.encoded.byteArray).flatMap {
        case None        => F.pure(None)
        case Some(bytes) => F.fromEither(RlpEncoded.coerce(BitVector(bytes)).decoded[V]).map(_.some)
      }

    override def toStream: Stream[F, (K, V)] =
      store.toStream(cf).evalMap(decodeTuple)

    override def toList: F[List[(K, V)]] =
      store.toList(cf).flatMap(_.traverse(decodeTuple))

    override def toMap: F[Map[K, V]] =
      toList.map(_.toMap)

    override def size: F[Int] =
      store.size(cf)

    private def encodeTuple(kv: (K, V)): (Array[Byte], Array[Byte]) =
      (kv._1.encoded.byteArray, kv._2.encoded.byteArray)

    private def decodeTuple(kv: (Array[Byte], Array[Byte])): F[(K, V)] =
      for {
        key   <- F.fromEither(RlpEncoded.coerce(BitVector(kv._1)).decoded[K])
        value <- F.fromEither(RlpEncoded.coerce(BitVector(kv._2)).decoded[V])
      } yield key -> value
  }
}
