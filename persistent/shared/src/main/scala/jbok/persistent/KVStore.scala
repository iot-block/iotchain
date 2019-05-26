package jbok.persistent

import fs2._
import jbok.codec.rlp.RlpCodec
import scodec.bits.ByteVector

trait KVStore[F[_]] {
  def put(cf: ColumnFamily, key: ByteVector, value: ByteVector): F[Unit]

  def get(cf: ColumnFamily, key: ByteVector): F[Option[ByteVector]]

  def getAs[A: RlpCodec](cf: ColumnFamily, key: ByteVector): F[Option[A]]

  def del(cf: ColumnFamily, key: ByteVector): F[Unit]

  def writeBatch(cf: ColumnFamily, puts: List[(ByteVector, ByteVector)], dels: List[ByteVector]): F[Unit]

  def writeBatch(cf: ColumnFamily, ops: List[(ByteVector, Option[ByteVector])]): F[Unit]

  def writeBatch(puts: List[Put], dels: List[Del]): F[Unit]

  def toStream(cf: ColumnFamily): Stream[F, (ByteVector, ByteVector)]

  def toList(cf: ColumnFamily): F[List[(ByteVector, ByteVector)]]

  def toMap(cf: ColumnFamily): F[Map[ByteVector, ByteVector]]

  def size(cf: ColumnFamily): F[Int]
}
