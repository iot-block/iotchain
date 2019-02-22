package jbok.persistent.leveldb

import java.io.File

import cats.effect._
import cats.implicits._
import fs2._
import jbok.codec.rlp.RlpCodec
import jbok.common.metrics.Metrics
import jbok.persistent.KeyValueDB
import org.fusesource.leveldbjni.JniDBFactory.{factory => JNIFactory}
import org.iq80.leveldb._
import org.iq80.leveldb.impl.Iq80DBFactory.factory
import scodec.bits.ByteVector

final class LevelDB[F[_]](
    path: String,
    db: DB,
    options: Options,
    readOptions: ReadOptions,
    writeOptions: WriteOptions
)(implicit F: Sync[F], T: Timer[F], M: Metrics[F])
    extends KeyValueDB[F] {

  def close: F[Unit] =
    F.delay(db.close())

  override protected[jbok] def getRaw(key: ByteVector): F[Option[ByteVector]] = M.timeF("leveldb_get") {
    F.delay(db.get(key.toArray, readOptions)).map(ByteVector.apply).attemptT.toOption.value
  }

  override protected[jbok] def putRaw(key: ByteVector, newVal: ByteVector): F[Unit] = M.timeF("leveldb_put") {
    F.delay(db.put(key.toArray, newVal.toArray, writeOptions))
  }

  override protected[jbok] def delRaw(key: ByteVector): F[Unit] = M.timeF("leveldb_del") {
    F.delay(db.delete(key.toArray))
  }

  override protected[jbok] def hasRaw(key: ByteVector): F[Boolean] =
    getRaw(key).map(_.isDefined)

  override protected[jbok] def keysRaw: F[List[ByteVector]] =
    stream(None).map(_._1).compile.toList

  override protected[jbok] def size: F[Int] =
    keysRaw.map(_.length)

  override protected[jbok] def toMapRaw: F[Map[ByteVector, ByteVector]] =
    stream(None).compile.toList.map(_.toMap)

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

  override protected[jbok] def writeBatchRaw(put: List[(ByteVector, ByteVector)], del: List[ByteVector]): F[Unit] =
    M.timeF("leveldb_batch") {
      for {
        batch <- createWriteBatch
        _     <- F.delay(put.map { case (k, v) => batch.put(k.toArray, v.toArray) })
        _ <- F.delay(del.map { k =>
          batch.delete(k.toArray)
        })
        _ <- write(batch)
      } yield ()
    }

  private def createWriteBatch: F[WriteBatch] =
    F.delay(db.createWriteBatch())

  private def write(writeBatch: WriteBatch): F[Unit] =
    F.delay(db.write(writeBatch, writeOptions))

  private def iterator(start: Option[Array[Byte]] = None): Resource[F, DBIterator] =
    Resource
      .make(
        F.delay(db.iterator(readOptions))
          .map(it => {
            start match {
              case Some(b) => it.seek(b)
              case None    => it.seekToFirst()
            }
            it
          }))(it => F.delay(it.close()))

  private def stream(start: Option[Array[Byte]]): Stream[F, (ByteVector, ByteVector)] =
    Stream.resource(iterator(start)).flatMap { iter =>
      Stream.unfoldEval[F, DBIterator, (ByteVector, ByteVector)](iter)(
        iter =>
          for {
            hn <- F.delay(iter.hasNext)
            opt <- if (hn) F.delay((entry2tuple(iter.next()) -> iter).some)
            else none.pure[F]
          } yield opt
      )
    }

  private def entry2tuple(entry: java.util.Map.Entry[Array[Byte], Array[Byte]]): (ByteVector, ByteVector) =
    ByteVector(entry.getKey) -> ByteVector(entry.getValue)
}

object LevelDB {
  private[this] val log = jbok.common.log.getLogger("LevelDB")

  val defaultOptions = (new Options).createIfMissing(true)

  val defaultReadOptions = new ReadOptions

  val defaultWriteOptions = new WriteOptions

  def apply[F[_]](
      path: String,
      useJni: Boolean = false,
      options: Options = defaultOptions,
      readOptions: ReadOptions = defaultReadOptions,
      writeOptions: WriteOptions = defaultWriteOptions
  )(implicit F: Sync[F], T: Timer[F], M: Metrics[F]): F[KeyValueDB[F]] =
    iq80[F](path, options, readOptions, writeOptions).attemptT
      .getOrElseF(jni[F](path, options, readOptions, writeOptions))
      .map(_.asInstanceOf[KeyValueDB[F]])

  private[jbok] def iq80[F[_]](
      path: String,
      options: Options = defaultOptions,
      readOptions: ReadOptions = defaultReadOptions,
      writeOptions: WriteOptions = defaultWriteOptions
  )(implicit F: Sync[F], T: Timer[F], M: Metrics[F]): F[LevelDB[F]] =
    for {
      db <- F.delay(factory.open(new File(path), options))
      _  <- F.delay(log.info(s"open db at ${path}"))
    } yield new LevelDB[F](path, db, options, readOptions, writeOptions)

  private[jbok] def jni[F[_]](
      path: String,
      options: Options = defaultOptions,
      readOptions: ReadOptions = defaultReadOptions,
      writeOptions: WriteOptions = defaultWriteOptions
  )(implicit F: Sync[F], T: Timer[F], M: Metrics[F]): F[LevelDB[F]] =
    for {
      db <- F.delay(JNIFactory.open(new File(path), options))
      _  <- F.delay(log.info(s"open db at ${path}"))
    } yield new LevelDB[F](path, db, options, readOptions, writeOptions)

  def destroy[F[_]](path: String, useJni: Boolean = false, options: Options = defaultOptions)(
      implicit F: Sync[F]): F[Unit] =
    if (useJni)
      F.delay(JNIFactory.destroy(new File(path), options))
    else
      F.delay(factory.destroy(new File(path), options))
}
