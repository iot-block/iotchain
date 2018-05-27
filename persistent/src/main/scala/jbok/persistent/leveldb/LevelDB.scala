package jbok.persistent.leveldb

import java.io.File

import cats.Traverse
import cats.effect._
import cats.implicits._
import fs2._
import org.iq80.leveldb._
import org.iq80.leveldb.impl.Iq80DBFactory.factory
import scodec.Codec
import scodec.bits.BitVector

import scala.collection.mutable

case class LevelDBConfig(path: String, options: Options = LevelDB.defaultOptions)

object LevelDB {
  val defaultOptions = (new Options).createIfMissing(true)

  val defaultReadOptions = new ReadOptions

  val defaultWriteOptions = new WriteOptions

  val pool = mutable.Map[String, DB]()

  def repair[F[_]](config: LevelDBConfig)(implicit F: Effect[F]) =
    F.delay(factory.repair(new File(config.path), config.options))

  def destroy[F[_]](config: LevelDBConfig)(implicit F: Effect[F]) =
    F.delay(factory.destroy(new File(config.path), config.options))

  def apply[F[_]](config: LevelDBConfig)(implicit F: Effect[F]): F[LevelDB[F]] =
    F.delay {
        pool.getOrElseUpdate(config.path, factory.open(new File(config.path), config.options))
      }
      .map(db => new LevelDB[F](db, F.delay { db.close(); pool -= config.path }, destroy[F](config), repair[F](config)))

  private[leveldb] sealed trait WriteOp extends Any
  private[leveldb] case class PutOp(kv: (Array[Byte], Array[Byte])) extends AnyVal with WriteOp
  private[leveldb] case class DeleteOp(key: Array[Byte]) extends AnyVal with WriteOp
}

case class LevelDB[F[_]](db: DB, close: F[Unit], destroy: F[Unit], repair: F[Unit])(implicit F: Effect[F]) {
  import LevelDB._

  def get(key: Array[Byte], options: ReadOptions): F[Array[Byte]] =
    F.delay(db.get(key, options))

  def get[K, V](key: K, options: ReadOptions = defaultReadOptions)(implicit ck: Codec[K], cv: Codec[V]): F[V] =
    for {
      rawK <- F.delay(encode(key))
      rawV <- get(rawK, options)
      v <- F.delay(decode[V](rawV))
    } yield v

  def put(key: Array[Byte], value: Array[Byte], options: WriteOptions): F[Snapshot] =
    F.delay(db.put(key, value, options))

  def put[K, V](key: K, value: V, options: WriteOptions = defaultWriteOptions)(
      implicit ck: Codec[K],
      cv: Codec[V]): F[Snapshot] =
    for {
      rawK <- F.delay(encode(key))
      rawV <- F.delay(encode(value))
      snapshot <- put(rawK, rawV, options)
    } yield snapshot

  def putOp[K, V](key: K, value: V)(implicit ck: Codec[K], cv: Codec[V]): F[WriteOp] = {
    for {
      rawK <- F.delay(encode(key))
      rawV <- F.delay(encode(value))
    } yield PutOp((rawK, rawV))
  }

  def delete(key: Array[Byte], options: WriteOptions): F[Snapshot] =
    F.delay(db.delete(key, options))

  def delete[K](key: K, options: WriteOptions = defaultWriteOptions)(implicit ck: Codec[K]): F[Snapshot] =
    for {
      rawK <- F.delay(encode(key))
      snapshot <- delete(rawK, options)
    } yield snapshot

  def deleteOp[K](key: K)(implicit ck: Codec[K]): F[WriteOp] = {
    for {
      rawK <- F.delay(encode(key))
    } yield DeleteOp(rawK)
  }

  def createWriteBatch: F[WriteBatch] =
    F.delay(db.createWriteBatch())

  def write(writeBatch: WriteBatch, options: WriteOptions = defaultWriteOptions): F[Snapshot] =
    F.delay(db.write(writeBatch, options))

  def iterator(options: ReadOptions = defaultReadOptions): F[DBIterator] =
    F.delay(db.iterator(options))

  def compactRange(begin: Array[Byte], end: Array[Byte]): F[Unit] =
    F.delay(db.compactRange(begin, end))

  def suspendCompactions: F[Unit] =
    F.delay(db.suspendCompactions())

  def resumeCompactions: F[Unit] =
    F.delay(db.resumeCompactions())

  // ----------------------------------

  def writeBatch[G[_]: Traverse](ops: G[F[WriteOp]], options: WriteOptions = defaultWriteOptions): F[Snapshot] = {
    for {
      batch <- createWriteBatch
      _ <- ops.sequence.map(_.map {
        case PutOp(kv) => batch.put(kv._1, kv._2)
        case DeleteOp(key) => batch.delete(key)
      })
      snapshot <- write(batch, options)
    } yield snapshot
  }

  def decode[A](x: Array[Byte])(implicit codec: Codec[A]): A = codec.decode(BitVector(x).compact).require.value

  def encode[A](x: A)(implicit codec: Codec[A]): Array[Byte] = codec.encode(x).require.toByteArray

  @inline
  def entry2tuple(entry: java.util.Map.Entry[Array[Byte], Array[Byte]]): (Array[Byte], Array[Byte]) =
    entry.getKey -> entry.getValue

  def rawStream(options: ReadOptions = defaultReadOptions): Stream[F, (Array[Byte], Array[Byte])] = {
    Stream.bracket(iterator(options))(iter => {
      Stream.unfold(iter)(iter => if (iter.hasNext) Some((entry2tuple(iter.next()), iter)) else None)
    }, iter => F.delay(iter.close()))
  }

  def stream[K, V](options: ReadOptions = defaultReadOptions)(implicit ck: Codec[K], cv: Codec[V]): Stream[F, (K, V)] =
    rawStream(options).map { case (key, value) => decode[K](key) -> decode[V](value) }
}
