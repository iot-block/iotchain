package jbok.persistent
import java.nio.file.Paths

import cats.effect.{Resource, Sync}
import jbok.persistent.rocksdb.RocksDB

object KeyValueDBPlatform {
  def resource[F[_]](config: PersistConfig)(implicit F: Sync[F]): Resource[F, KeyValueDB[F]] =
    config.driver match {
      case "inmem"   => Resource.liftF(KeyValueDB.inmem[F])
      case "rocksdb" => RocksDB.resource[F](Paths.get(config.path))
      case driver    => Resource.liftF(F.raiseError(new IllegalArgumentException(s"database driver=${driver} is not supported")))
    }
}
