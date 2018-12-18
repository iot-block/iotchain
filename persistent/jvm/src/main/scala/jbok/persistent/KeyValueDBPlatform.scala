package jbok.persistent

import cats.effect.{Sync, Timer}
import jbok.common.metrics.Metrics
import jbok.persistent.leveldb.LevelDB

trait KeyValueDBPlatform {

  def _forPath[F[_]: Sync](path: String)(implicit T: Timer[F], M: Metrics[F]): F[KeyValueDB[F]] = path match {
    case KeyValueDB.INMEM => KeyValueDB.inmem[F]
    case x                => LevelDB[F](x)
  }
}
