package jbok.persistent.rocksdb

import java.nio.file.Path
import java.util

import cats.effect.{Resource, Sync}
import cats.implicits._
import fs2._
import jbok.common.FileUtil
import jbok.common.log.Logger
import jbok.persistent.{ColumnFamily, Del, KVStore, Put}
import org.rocksdb.{RocksDB => Underlying, Logger => _, _}

import scala.collection.JavaConverters._

final class RocksKVStore[F[_]](db: Underlying, readOptions: ReadOptions, writeOptions: WriteOptions, cfs: Map[ColumnFamily, ColumnFamilyHandle])(implicit F: Sync[F])
    extends KVStore[F] {
  override def put(cf: ColumnFamily, key: Array[Byte], value: Array[Byte]): F[Unit] =
    F.delay(db.put(handle(cf), writeOptions, key, value))

  override def del(cf: ColumnFamily, key: Array[Byte]): F[Unit] =
    F.delay(db.delete(handle(cf), writeOptions, key))

  override def writeBatch(cf: ColumnFamily, puts: List[(Array[Byte], Array[Byte])], dels: List[Array[Byte]]): F[Unit] =
    batchResource.use { batch =>
      F.delay {
        puts.foreach { case (key, value) => batch.put(handle(cf), key, value) }
        dels.foreach { key =>
          batch.delete(handle(cf), key)
        }
        db.write(writeOptions, batch)
      }
    }

  override def writeBatch(cf: ColumnFamily, ops: List[(Array[Byte], Option[Array[Byte]])]): F[Unit] =
    batchResource.use { batch =>
      F.delay {
        ops.foreach {
          case (key, Some(value)) => batch.put(handle(cf), key, value)
          case (key, None)        => batch.delete(handle(cf), key)
        }
        db.write(writeOptions, batch)
      }
    }

  override def writeBatch(puts: List[Put], dels: List[Del]): F[Unit] =
    batchResource.use { batch =>
      F.delay {
        puts.foreach { case (cf, key, value) => batch.put(handle(cf), key, value) }
        dels.foreach { case (cf, key)        => batch.delete(handle(cf), key) }
        db.write(writeOptions, batch)
      }
    }

  override def get(cf: ColumnFamily, key: Array[Byte]): F[Option[Array[Byte]]] =
    F.delay(Option(db.get(handle(cf), readOptions, key)))

  override def toStream(cf: ColumnFamily): Stream[F, (Array[Byte], Array[Byte])] = {
    val iterator = Resource {
      F.delay(db.newIterator(handle(cf), readOptions))
        .map { it =>
          it.seekToFirst()
          it -> F.delay(it.close())
        }
    }

    Stream.resource(iterator).flatMap { iter =>
      Stream.unfoldEval[F, RocksIterator, (Array[Byte], Array[Byte])](iter)(
        iter =>
          for {
            hn <- F.delay(iter.isValid)
            opt <- if (hn) {
              F.delay(iter.next()).as(Some((iter.key(), iter.value()) -> iter))
            } else {
              F.pure(None)
            }
          } yield opt
      )
    }
  }

  override def toList(cf: ColumnFamily): F[List[(Array[Byte], Array[Byte])]] =
    toStream(cf).compile.toList

  override def toMap(cf: ColumnFamily): F[Map[Array[Byte], Array[Byte]]] =
    toStream(cf).compile.toList.map(_.toMap)

  override def size(cf: ColumnFamily): F[Int] =
    toList(cf).map(_.length)

  private val batchResource: Resource[F, WriteBatch] = Resource {
    F.delay(new WriteBatch()).map { batch =>
      batch -> F.delay(batch.close())
    }
  }

  private def handle(cf: ColumnFamily): ColumnFamilyHandle =
    cfs(cf)
}

object RocksKVStore {
  val defaultOptions: DBOptions = new DBOptions()
    .setCreateIfMissing(true)
    .setCreateMissingColumnFamilies(true)

  val defaultReadOptions = new ReadOptions()

  val defaultWriteOptions = new WriteOptions()

  val defaultColumnFamilyOptions = new ColumnFamilyOptions()

  def resource[F[_]](
      path: Path,
      columnFamilies: List[ColumnFamily],
      options: DBOptions = defaultOptions,
      readOptions: ReadOptions = defaultReadOptions,
      writeOptions: WriteOptions = defaultWriteOptions
  )(implicit F: Sync[F]): Resource[F, KVStore[F]] =
    Resource {
      for {
        _ <- FileUtil[F].open(path, create = true, asDirectory = true)
        descriptors = columnFamilies.map(cf => new ColumnFamilyDescriptor(cf.bytes, defaultColumnFamilyOptions)).asJava
        handles     = new util.ArrayList[ColumnFamilyHandle]()
        underlying <- F.delay(Underlying.open(options, path.toString, descriptors, handles))
        _          <- Logger[F].i(s"open rocksdb at path=${path} with ${columnFamilies.mkString(",")}")
        store = new RocksKVStore[F](underlying, readOptions, writeOptions, columnFamilies.zip(handles.asScala).toMap)
      } yield store -> (F.delay(underlying.closeE()) >> Logger[F].i(s"closed rocksdb at path=${path}"))
    }

  def destroy[F[_]](path: Path)(implicit F: Sync[F]): F[Unit] =
    F.delay(Underlying.destroyDB(path.toString, new Options()))
}
