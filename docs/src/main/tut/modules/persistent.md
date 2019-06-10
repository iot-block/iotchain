---
layout: docsplus
title:  "Persistent"
number: 6
---

`Persistent` module contains KVStore interfaces and some backends.

The simplest KVStore interface with `ColumnFamily` support  

```scala mdoc:silent
import jbok.persistent._
import fs2.Stream
 
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

```

and a simple one-column KVStore interface

```scala mdoc
import jbok.persistent._
import fs2.Stream

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
```
