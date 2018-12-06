package jbok.persistent.leveldb

import java.io.File

import cats.effect._
import cats.implicits._
import fs2._
import jbok.persistent.KeyValueDB
import org.fusesource.leveldbjni.JniDBFactory.{factory => JNIFactory}
import org.iq80.leveldb._
import org.iq80.leveldb.impl.Iq80DBFactory.factory
import scodec.Codec
import scodec.bits.ByteVector

final class LevelDB[F[_]](
    path: String,
    db: DB,
    options: Options,
    readOptions: ReadOptions,
    writeOptions: WriteOptions
)(implicit F: Sync[F])
    extends KeyValueDB[F] {

  def close: F[Unit] =
    F.delay(db.close())

  override protected[jbok] def getRaw(key: ByteVector): F[Option[ByteVector]] =
    F.delay(db.get(key.toArray, readOptions)).map(ByteVector.apply).attemptT.toOption.value

  override protected[jbok] def putRaw(key: ByteVector, newVal: ByteVector): F[Unit] =
    F.delay(db.put(key.toArray, newVal.toArray, writeOptions))

  override protected[jbok] def delRaw(key: ByteVector): F[Unit] =
    F.delay(db.delete(key.toArray))

  override protected[jbok] def hasRaw(key: ByteVector): F[Boolean] =
    getRaw(key).map(_.isDefined)

  override protected[jbok] def keysRaw: F[List[ByteVector]] =
    stream(None).map(_._1).compile.toList

  override protected[jbok] def size: F[Int] =
    keysRaw.map(_.length)

  override protected[jbok] def toMapRaw: F[Map[ByteVector, ByteVector]] =
    stream(None).compile.toList.map(_.toMap)

  override def keys[Key: Codec](namespace: ByteVector): F[List[Key]] =
    stream(Some(namespace.toArray))
      .map(_._1)
      .compile
      .toList
      .flatMap(_.traverse(k => decode[Key](k, namespace)))

  override def toMap[Key: Codec, Val: Codec](namespace: ByteVector): F[Map[Key, Val]] =
    for {
      mapRaw <- stream(Some(namespace.toArray)).compile.toList.map(_.toMap)
      xs     <- mapRaw.toList.traverse { case (k, v) => (decode[Key](k, namespace), decode[Val](v)).tupled }
    } yield xs.toMap

  override protected[jbok] def writeBatchRaw(put: List[(ByteVector, ByteVector)], del: List[ByteVector]): F[Unit] =
    for {
      batch <- createWriteBatch
      _     <- F.delay(put.map { case (k, v) => batch.put(k.toArray, v.toArray) })
      _ <- F.delay(del.map { k =>
        batch.delete(k.toArray)
      })
      _ <- write(batch)
    } yield ()

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
  val defaultOptions = (new Options).createIfMissing(true)

  val defaultReadOptions = new ReadOptions

  val defaultWriteOptions = new WriteOptions

  def apply[F[_]](
      path: String,
      useJni: Boolean = true,
      options: Options = defaultOptions,
      readOptions: ReadOptions = defaultReadOptions,
      writeOptions: WriteOptions = defaultWriteOptions
  )(implicit F: Sync[F]): F[KeyValueDB[F]] =
    if (useJni) jni[F](path, options, readOptions, writeOptions)
    else iq80[F](path, options, readOptions, writeOptions)

  private[jbok] def iq80[F[_]](
      path: String,
      options: Options = defaultOptions,
      readOptions: ReadOptions = defaultReadOptions,
      writeOptions: WriteOptions = defaultWriteOptions
  )(implicit F: Sync[F]): F[KeyValueDB[F]] =
    for {
      db <- F.delay(factory.open(new File(path), options))
    } yield new LevelDB[F](path, db, options, readOptions, writeOptions)

  private[jbok] def jni[F[_]](
      path: String,
      options: Options = defaultOptions,
      readOptions: ReadOptions = defaultReadOptions,
      writeOptions: WriteOptions = defaultWriteOptions
  )(implicit F: Sync[F]): F[KeyValueDB[F]] =
    for {
      db <- F.delay(JNIFactory.open(new File(path), options))
    } yield new LevelDB[F](path, db, options, readOptions, writeOptions)

  def destroy[F[_]](path: String, useJni: Boolean = true, options: Options = defaultOptions)(
      implicit F: Sync[F]): F[Unit] =
    if (useJni)
      F.delay(JNIFactory.destroy(new File(path), options))
    else
      F.delay(factory.destroy(new File(path), options))
}
