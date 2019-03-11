package jbok.persistent

import cats.effect.{Sync, Timer}
import jbok.common.metrics.Metrics
import jbok.persistent.leveldb.LevelDB
import jbok.persistent.rocksdb.Rocks

trait KeyValueDBPlatform {

  def _forBackendAndPath[F[_]: Sync](backend: String, path: String)(implicit T: Timer[F],
                                                                    M: Metrics[F]): F[KeyValueDB[F]] =
    backend match {
      case "inmem"   => KeyValueDB.inmem[F]
      case "leveldb" => LevelDB[F](path)
      case "rocksdb" => Rocks[F](path)
      case x         => throw new Exception(s"backend ${x} is not supported")
    }
}
