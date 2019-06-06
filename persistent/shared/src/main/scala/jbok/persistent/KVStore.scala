package jbok.persistent

import fs2._

trait KVStore[F[_]] {
  // write
  def put(cf: ColumnFamily, key: Array[Byte], value: Array[Byte]): F[Unit]

  def del(cf: ColumnFamily, key: Array[Byte]): F[Unit]

  def writeBatch(cf: ColumnFamily, puts: List[(Array[Byte], Array[Byte])], dels: List[Array[Byte]]): F[Unit]

  def writeBatch(cf: ColumnFamily, ops: List[(Array[Byte], Option[Array[Byte]])]): F[Unit]

  def writeBatch(puts: List[Put], dels: List[Del]): F[Unit]

  // read
  def get(cf: ColumnFamily, key: Array[Byte]): F[Option[Array[Byte]]]

  def toStream(cf: ColumnFamily): Stream[F, (Array[Byte], Array[Byte])]

  def toList(cf: ColumnFamily): F[List[(Array[Byte], Array[Byte])]]

  def toMap(cf: ColumnFamily): F[Map[Array[Byte], Array[Byte]]]

  def size(cf: ColumnFamily): F[Int]
}
