package jbok.persistent.rocksdb

import java.nio.file.Path
import java.util

import scala.collection.JavaConverters._
import cats.effect.{Resource, Sync}
import cats.implicits._
import fs2._
import jbok.codec.rlp.RlpCodec
import jbok.common.FileUtil
import jbok.common.log.Logger
import jbok.persistent.{ColumnFamily, Del, KVStore, Put}
import org.rocksdb.{RocksDB => Underlying, Logger => _, _}
import scodec.bits.ByteVector
import jbok.codec.rlp.implicits._

final class RocksKVStore[F[_]](db: Underlying, readOptions: ReadOptions, writeOptions: WriteOptions, cfs: Map[ColumnFamily, ColumnFamilyHandle])(implicit F: Sync[F])
    extends KVStore[F] {
  override def put(cf: ColumnFamily, key: ByteVector, value: ByteVector): F[Unit] =
    F.delay(db.put(handle(cf), writeOptions, key.toArray, value.toArray))

  override def get(cf: ColumnFamily, key: ByteVector): F[Option[ByteVector]] =
    F.delay(Option(db.get(handle(cf), readOptions, key.toArray)).map(ByteVector.apply))

  override def getAs[A: RlpCodec](cf: ColumnFamily, key: ByteVector): F[Option[A]] =
    get(cf, key).flatMap {
      case Some(value) => F.fromEither(value.asEither[A]).map(_.some)
      case None        => F.pure(None)
    }

  override def del(cf: ColumnFamily, key: ByteVector): F[Unit] =
    F.delay(db.delete(handle(cf), writeOptions, key.toArray))

  override def writeBatch(cf: ColumnFamily, puts: List[(ByteVector, ByteVector)], dels: List[ByteVector]): F[Unit] =
    batchResource.use { batch =>
      F.delay {
        puts.foreach { case (key, value) => batch.put(handle(cf), key.toArray, value.toArray) }
        dels.foreach { key =>
          batch.delete(handle(cf), key.toArray)
        }
        db.write(writeOptions, batch)
      }
    }

  override def writeBatch(cf: ColumnFamily, ops: List[(ByteVector, Option[ByteVector])]): F[Unit] =
    batchResource.use { batch =>
      F.delay {
        ops.foreach {
          case (key, Some(value)) => batch.put(handle(cf), key.toArray, value.toArray)
          case (key, None)        => batch.delete(handle(cf), key.toArray)
        }
        db.write(writeOptions, batch)
      }
    }

  override def writeBatch(puts: List[Put], dels: List[Del]): F[Unit] =
    batchResource.use { batch =>
      F.delay {
        puts.foreach { case (cf, key, value) => batch.put(handle(cf), key.toArray, value.toArray) }
        dels.foreach { case (cf, key)        => batch.delete(handle(cf), key.toArray) }
        db.write(writeOptions, batch)
      }
    }

  override def toStream(cf: ColumnFamily): Stream[F, (ByteVector, ByteVector)] = {
    val iterator = Resource {
      F.delay(db.newIterator(handle(cf), readOptions))
        .map { it =>
          it.seekToFirst()
          it -> F.delay(it.close())
        }
    }

    Stream.resource(iterator).flatMap { iter =>
      Stream.unfoldEval[F, RocksIterator, (ByteVector, ByteVector)](iter)(
        iter =>
          for {
            hn <- F.delay(iter.isValid)
            opt <- if (hn) {
              F.delay(iter.next()).as(Some((ByteVector(iter.key()), ByteVector(iter.value())) -> iter))
            } else {
              none.pure[F]
            }
          } yield opt
      )
    }
  }

  override def toList(cf: ColumnFamily): F[List[(ByteVector, ByteVector)]] =
    toStream(cf).compile.toList

  override def toMap(cf: ColumnFamily): F[Map[ByteVector, ByteVector]] =
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
      writeOptions: WriteOptions = defaultWriteOptions,
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
