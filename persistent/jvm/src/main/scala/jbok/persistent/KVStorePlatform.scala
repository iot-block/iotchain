package jbok.persistent
import java.nio.file.Paths

import cats.effect.{Resource, Sync}
import jbok.persistent.rocksdb.RocksKVStore

object KVStorePlatform {
  def resource[F[_]](config: PersistConfig)(implicit F: Sync[F]): Resource[F, KVStore[F]] =
    config.driver match {
      case "inmem"   => Resource.liftF(MemoryKVStore[F])
      case "rocksdb" => RocksKVStore.resource[F](Paths.get(config.path), config.columnFamilies.map(ColumnFamily.apply))
      case driver    => Resource.liftF(F.raiseError(new IllegalArgumentException(s"database driver=${driver} is not supported")))
    }
}
