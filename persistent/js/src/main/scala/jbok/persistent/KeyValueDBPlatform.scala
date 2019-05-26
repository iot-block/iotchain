package jbok.persistent

import cats.effect.{Resource, Sync}

object KeyValueDBPlatform {
  def resource[F[_]](config: PersistConfig)(implicit F: Sync[F]): Resource[F, KVStore[F]] =
    config.driver match {
      case "memory" => Resource.liftF(MemoryKVStore[F])
      case driver  => Resource.liftF(F.raiseError(new IllegalArgumentException(s"database driver=${driver} is not supported")))
    }
}
