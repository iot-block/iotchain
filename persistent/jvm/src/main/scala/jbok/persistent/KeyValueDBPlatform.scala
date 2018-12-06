package jbok.persistent

import cats.effect.Sync
import jbok.persistent.leveldb.LevelDB

trait KeyValueDBPlatform {

  def _forPath[F[_]: Sync](path: String): F[KeyValueDB[F]] = path match {
    case KeyValueDB.INMEM => KeyValueDB.inmem[F]
    case x                => LevelDB[F](x)
  }
}
