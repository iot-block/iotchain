package jbok.persistent.leveldb

import java.io.File

import cats.Traverse
import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2._
import jbok.persistent.KeyValueDB
import org.iq80.leveldb._
import org.iq80.leveldb.impl.Iq80DBFactory.factory
import scodec.bits.ByteVector

case class LevelDBConfig(path: String, options: Options = LevelDB.defaultOptions)

object LevelDB {
  val defaultOptions = (new Options).createIfMissing(true)

  val defaultReadOptions = new ReadOptions

  val defaultWriteOptions = new WriteOptions

  val pool: Ref[IO, Map[String, DB]] = Ref.of[IO, Map[String, DB]](Map.empty).unsafeRunSync()

  def open[F[_]](config: LevelDBConfig)(implicit F: Effect[F]): F[DB] =
    for {
      db <- F.delay(factory.open(new File(config.path), config.options))
      _ <- F.liftIO(pool.update(_ + (config.path -> db)))
    } yield db

  def apply[F[_]](config: LevelDBConfig)(implicit F: Effect[F]): F[LevelDB[F]] =
    for {
      m <- F.liftIO(pool.get)
      db <- m.get(config.path) match {
        case Some(db) => db.pure[F]
        case None => open[F](config)
      }
      dbRef <- Ref.of[F, DB](db)
      leveldb = new LevelDB[F](
        dbRef,
        F.delay(db.close()) *> F.liftIO(pool.update(_ - config.path).void),
        F.delay(factory.destroy(new File(config.path), config.options)),
        open[F](config).flatMap(db => dbRef.set(db))
      )
    } yield leveldb
}

case class LevelDB[F[_]](db: Ref[F, DB], close: F[Unit], destroy: F[Unit], reopen: F[Unit])(implicit F: Effect[F]) extends KeyValueDB[F] {
  import LevelDB._

  override def get(key: ByteVector): F[ByteVector] =
    db.get.map(_.get(key.toArray, defaultReadOptions)).map(ByteVector.apply)

  override def getOpt(key: ByteVector): F[Option[ByteVector]] = get(key).attempt.map {
    case Left(_)  => none
    case Right(v) => v.some
  }

  override def put(key: ByteVector, newVal: ByteVector): F[Unit] =
    db.get.map(_.put(key.toArray, newVal.toArray, defaultWriteOptions))

  override def del(key: ByteVector): F[Unit] =
    db.get.map(_.delete(key.toArray))

  override def has(key: ByteVector): F[Boolean] = getOpt(key).map(_.isDefined)

  override def keys: F[List[ByteVector]] = stream().map(_._1).compile.toList

  override def size: F[Int] = keys.map(_.length)

  override def toMap: F[Map[ByteVector, ByteVector]] =
    keys.flatMap(_.map(k => get(k).map(v => k -> v)).sequence.map(_.toMap))

  override def clear(): F[Unit] = destroy *> close *> reopen *> destroy

  override def writeBatch[G[_]: Traverse](ops: G[(ByteVector, Option[ByteVector])]): F[Unit] =
    for {
      batch <- createWriteBatch
      _ <- ops.traverse {
        case (key, Some(v)) => F.delay(batch.put(key.toArray, v.toArray))
        case (key, None)    => F.delay(batch.delete(key.toArray))
      }
      _ <- write(batch, defaultWriteOptions)
    } yield ()

  // ---

  def createWriteBatch: F[WriteBatch] =
    db.get.map(_.createWriteBatch())

  def write(writeBatch: WriteBatch, options: WriteOptions = defaultWriteOptions): F[Unit] =
    db.get.map(_.write(writeBatch, options))

  def iterator(options: ReadOptions = defaultReadOptions): F[DBIterator] =
    db.get.map(_.iterator(options))

  def stream(options: ReadOptions = defaultReadOptions): Stream[F, (ByteVector, ByteVector)] =
    Stream.bracket(iterator(options))(iter => F.delay(iter.close()))
        .flatMap( iter =>
          Stream.unfoldEval[F, DBIterator, (ByteVector, ByteVector)](iter)(
            iter =>
              for {
                hn <- F.delay(iter.hasNext)
                opt <- if (hn) F.delay((entry2tuple(iter.next()) -> iter).some)
                else none.pure[F]
              } yield opt
          )
        )

  @inline
  private def entry2tuple(entry: java.util.Map.Entry[Array[Byte], Array[Byte]]): (ByteVector, ByteVector) =
    ByteVector(entry.getKey) -> ByteVector(entry.getValue)
}
