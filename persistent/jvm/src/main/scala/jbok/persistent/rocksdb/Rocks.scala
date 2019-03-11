package jbok.persistent.rocksdb

import cats.effect.{Resource, Sync, Timer}
import cats.implicits._
import fs2._
import jbok.codec.rlp.RlpCodec
import jbok.common.metrics.Metrics
import jbok.persistent.KeyValueDB
import org.rocksdb._
import scodec.bits.ByteVector

final class Rocks[F[_]](
    path: String,
    db: RocksDB,
    options: Options,
    readOptions: ReadOptions,
    writeOptions: WriteOptions
)(implicit F: Sync[F], T: Timer[F], M: Metrics[F])
    extends KeyValueDB[F] {

  override protected[jbok] def close: F[Unit] =
    F.delay(db.close())

  override protected[jbok] def getRaw(key: ByteVector): F[Option[ByteVector]] = F.delay {
    Option(db.get(readOptions, key.toArray)).map(ByteVector.apply)
  }

  override protected[jbok] def putRaw(key: ByteVector, newVal: ByteVector): F[Unit] = F.delay {
    db.put(writeOptions, key.toArray, newVal.toArray)
  }

  override protected[jbok] def delRaw(key: ByteVector): F[Unit] = F.delay {
    db.delete(writeOptions, key.toArray)
  }

  override protected[jbok] def hasRaw(key: ByteVector): F[Boolean] =
    getRaw(key).map(_.isDefined)

  override protected[jbok] def keysRaw: F[List[ByteVector]] =
    stream(None).map(_._1).compile.toList

  override protected[jbok] def size: F[Int] =
    keysRaw.map(_.length)

  override protected[jbok] def toMapRaw: F[Map[ByteVector, ByteVector]] =
    stream(None).compile.toList.map(_.toMap)

  override protected[jbok] def writeBatchRaw(put: List[(ByteVector, ByteVector)], del: List[ByteVector]): F[Unit] =
    F.delay {
      val batch = new WriteBatch()
      put.foreach { case (k, v) => batch.put(k.toArray, v.toArray) }
      del.foreach { k =>
        batch.delete(k.toArray)
      }
      db.write(writeOptions, batch)
    }

  override def keys[Key: RlpCodec](namespace: ByteVector): F[List[Key]] =
    stream(Some(namespace.toArray))
      .map(_._1)
      .compile
      .toList
      .flatMap(_.traverse(k => decode[Key](k, namespace)))

  override def toMap[Key: RlpCodec, Val: RlpCodec](namespace: ByteVector): F[Map[Key, Val]] =
    for {
      mapRaw <- stream(Some(namespace.toArray)).compile.toList.map(_.toMap)
      xs     <- mapRaw.toList.traverse { case (k, v) => (decode[Key](k, namespace), decode[Val](v)).tupled }
    } yield xs.toMap

  ////////////////////////////
  ////////////////////////////

  private def stream(start: Option[Array[Byte]]): Stream[F, (ByteVector, ByteVector)] =
    Stream.resource(iterator(start)).flatMap { iter =>
      Stream.unfoldEval[F, RocksIterator, (ByteVector, ByteVector)](iter)(
        iter =>
          for {
            hn <- F.delay(iter.isValid)
            opt <- if (hn) {
              F.delay(iter.next()).map { _ =>
                Some((ByteVector(iter.key()), ByteVector(iter.value())) -> iter)
              }
            } else none.pure[F]
          } yield opt
      )
    }

  private def iterator(start: Option[Array[Byte]] = None): Resource[F, RocksIterator] =
    Resource
      .make(
        F.delay(db.newIterator())
          .map(it => {
            start match {
              case Some(b) => it.seek(b)
              case None    => it.seekToFirst()
            }
            it
          }))(it => F.delay(it.close()))
}

object Rocks {
  RocksDB.loadLibrary()

  private[this] val log = jbok.common.log.getLogger("RocksDB")

  val defaultOptions = new Options().setCreateIfMissing(true)

  val defaultReadOptions = new ReadOptions()

  val defaultWriteOptions = new WriteOptions()

  def apply[F[_]](
      path: String,
      options: Options = defaultOptions,
      readOptions: ReadOptions = defaultReadOptions,
      writeOptions: WriteOptions = defaultWriteOptions
  )(implicit F: Sync[F], T: Timer[F], M: Metrics[F]): F[KeyValueDB[F]] = F.delay {
    val db = RocksDB.open(options, path)
    log.info(s"open db at ${path}")
    new Rocks[F](path, db, options, readOptions, writeOptions)
  }

  def destroy[F[_]](path: String, options: Options = defaultOptions)(implicit F: Sync[F]): F[Unit] =
    F.delay(RocksDB.destroyDB(path, options))
}
