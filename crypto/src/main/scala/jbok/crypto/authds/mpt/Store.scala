package jbok.crypto.authds.mpt

import cats.effect.Effect
import cats.implicits._
import jbok.persistent.leveldb.{LevelDB, LevelDBConfig}
import scodec.Codec

import scala.collection.mutable

object Store {
  def apply[F[_]: Effect, K: Codec, V: Codec](config: LevelDBConfig): F[Store[F, K, V]] = {
    for {
      db <- LevelDB[F](config)
    } yield new Store[F, K, V](db)
  }
}

class Store[F[_], K: Codec, V: Codec](db: LevelDB[F])(implicit F: Effect[F]) {
  val staged = mutable.Map[K, V]()

  def get(key: K): F[V] = {
    if (staged.contains(key)) {
      staged(key).pure[F]
    } else {
      db.get[K, V](key)
    }
  }

  def put(key: K, value: V): Unit = {
    staged += key -> value
  }

  def del(key: K): F[Unit] = {
    F.delay {
      staged -= key
      db.delete(key)
    }
  }

  def commit(): F[Unit] = {
    val batch = staged.map { case (k, v) =>
        db.putOp[K, V](k, v)
    }

    for {
      _ <- db.writeBatch(batch.toVector)
      _ = staged.clear()
    } yield ()
  }
}
